package eu.amadeos.apps.imagescan.service.scan.sane;

import eu.amadeos.commons.process.ProgramMissingException;
import eu.amadeos.apps.imagescan.FakeProcess;
import eu.amadeos.apps.imagescan.TestImages;
import eu.amadeos.apps.imagescan.domain.ColorMode;
import eu.amadeos.apps.imagescan.domain.ScanArea;
import eu.amadeos.apps.imagescan.domain.ScanSettings;
import eu.amadeos.apps.imagescan.domain.Scanner;
import eu.amadeos.apps.imagescan.domain.ScannerOptions;
import eu.amadeos.apps.imagescan.service.scan.ScanMonitor;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaneBackendTest {
    private static final Scanner EPSON = new Scanner("epson2:libusb:001:004", "Epson", "V39", "flatbed scanner");
    private static final ScannerOptions OPTIONS = SaneOptionParser.parseOptions(SaneOptionParserTest.EPSON);

    private final List<List<String>> commands = new ArrayList<>();

    private SaneBackend backend(Process process) {
        return new SaneBackend(command -> {
            commands.add(command);
            return process;
        });
    }

    @Test
    void listsScannersInItsOwnFormat() throws Exception {
        SaneBackend backend = backend(new FakeProcess("epson2:libusb:001:004\tEpson\tV39\tflatbed scanner\n", "", 0));
        assertEquals(List.of(EPSON), backend.listScanners());
        assertEquals(List.of("scanimage", "-f", "%d\t%v\t%m\t%t%n"), commands.getFirst());
    }

    @Test
    void fallsBackToTheClassicList() throws Exception {
        SaneBackend backend = backend(new FakeProcess("device `test:0' is a Noname frontend-tester virtual device\n", "", 0));
        assertEquals("test:0", backend.listScanners().getFirst().id());
    }

    // --8<-- [start:missing]
    @Test
    void reportsAMissingScanimage() {
        SaneBackend backend = new SaneBackend(command -> {
            // what CommandRunner.system() makes of the JDK's error
            throw ProgramMissingException.check("scanimage",
                    new IOException("Cannot run program \"scanimage\": error=2, No such file or directory"));
        });
        ProgramMissingException e = assertThrows(ProgramMissingException.class, backend::listScanners);
        assertEquals("scanimage", e.program());
    }
    // --8<-- [end:missing]

    @Test
    void readsTheOptions() throws Exception {
        SaneBackend backend = backend(new FakeProcess(SaneOptionParserTest.EPSON, "", 0));
        assertEquals(OPTIONS, backend.options(EPSON));
        assertEquals(List.of("scanimage", "-d", "epson2:libusb:001:004", "-A"), commands.getFirst());
    }

    @Test
    void scanCommandUsesTheScannersNamesAndMillimetres() {
        ScanSettings settings = new ScanSettings(250, ColorMode.GRAY, Optional.of(new ScanArea(10, 20.25, 100, 400)));
        assertEquals(List.of("scanimage", "-d", "epson2:libusb:001:004", "--format=pnm", "--progress",
                        "--mode=Gray", "--resolution=300", "-l", "10.0mm", "-t", "20.3mm", "-x", "100.0mm", "-y", "276.9mm"),
                SaneBackend.scanCommand(EPSON, settings, OPTIONS));
    }

    @Test
    void scanCommandFallsBackToAModeTheScannerHas() {
        ScannerOptions colourOnly = new ScannerOptions(List.of(300), Map.of(ColorMode.COLOR, "24bit Color"), 0, 0);
        List<String> command = SaneBackend.scanCommand(EPSON, ScanSettings.DEFAULT.withMode(ColorMode.LINEART), colourOnly);
        assertTrue(command.contains("--mode=24bit Color"));
        assertTrue(command.stream().noneMatch(a -> a.equals("-l")));
    }

    @Test
    void scansAndReportsProgress() throws Exception {
        byte[] ppm = TestImages.ppm(4, 3);
        SaneBackend backend = backend(new FakeProcess(ppm, "Progress: 12.5%\rProgress: 100.0%\r".getBytes(), 0));
        List<Double> progress = new CopyOnWriteArrayList<>();
        BufferedImage image = backend.scan(EPSON, ScanSettings.DEFAULT, OPTIONS, new ScanMonitor(progress::add));
        assertEquals(4, image.getWidth());
        assertEquals(TestImages.rgbAt(3, 2), image.getRGB(3, 2));
        assertTrue(progress.contains(0.125), progress.toString());
        assertEquals(1.0, progress.getLast(), 0);
    }

    @Test
    void failuresCarryScanimagesMessage() {
        SaneBackend backend = backend(new FakeProcess(new byte[0],
                "Progress: 0.0%\rscanimage: open of device epson2:libusb:001:004 failed: Device busy\n".getBytes(), 1));
        IOException e = assertThrows(IOException.class,
                () -> backend.scan(EPSON, ScanSettings.DEFAULT, OPTIONS, ScanMonitor.silent()));
        assertEquals("scanimage: open of device epson2:libusb:001:004 failed: Device busy", e.getMessage());
    }

    // --8<-- [start:cancel]
    @Test
    void cancelStopsScanimage() throws Exception {
        FakeProcess process = FakeProcess.hanging("P6 100 100 255\n".getBytes());
        SaneBackend backend = backend(process);
        ScanMonitor monitor = ScanMonitor.silent();
        CompletableFuture<BufferedImage> scan = CompletableFuture.supplyAsync(() -> {
            try {
                return backend.scan(EPSON, ScanSettings.DEFAULT, OPTIONS, monitor);
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        });
        Thread.sleep(100);
        monitor.cancel();
        ExecutionException e = assertThrows(ExecutionException.class, () -> scan.get(5, TimeUnit.SECONDS));
        assertInstanceOf(CancellationException.class, e.getCause());
        assertTrue(process.isDestroyed());
    }
    // --8<-- [end:cancel]

    @Test
    void cropsAfterwardsWithoutMillimetreGeometry() {
        BufferedImage page = TestImages.rgb(100, 100);
        BufferedImage part = SaneBackend.cropToArea(page, ScanArea.fromPixels(10, 20, 30, 40, 100), 100);
        assertEquals(30, part.getWidth());
        assertEquals(40, part.getHeight());
        assertEquals(TestImages.rgbAt(10, 20), part.getRGB(0, 0));
    }

    @Test
    void errorTextDropsProgress() {
        assertEquals("scanimage ended with exit code 7", SaneBackend.errorText("Progress: 5%\n", 7));
        assertEquals("a\nb", SaneBackend.errorText("a\nProgress: 1.0%\n b \n", 1));
    }
}
