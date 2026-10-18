package eu.amadeos.imagescan.service.scan.wia;

import eu.amadeos.imagescan.TestImages;
import eu.amadeos.imagescan.domain.ColorMode;
import eu.amadeos.imagescan.domain.ScanArea;
import eu.amadeos.imagescan.domain.ScanSettings;
import eu.amadeos.imagescan.domain.Scanner;
import eu.amadeos.imagescan.domain.ScannerOptions;
import eu.amadeos.imagescan.service.scan.ScanMonitor;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiaBackendTest {
    private static final Scanner CANON = new Scanner("{6BDD1FC6-810F-11D0-BEC7-08002BE2092F}\\0001", "", "Canon LiDE 300", "");

    /** Writes a small PNG, or waits until released. */
    private static final class FakeApi implements WiaApi {
        final List<Map<Integer, Integer>> scans = new ArrayList<>();
        CountDownLatch release;
        Path lastOutput;

        @Override
        public List<Device> devices() {
            return List.of(new Device("{ID}\\0001", "Canon", "Canon LiDE 300"),
                    new Device("{ID}\\0002", "Hewlett-Packard", "Scanjet G3110"));
        }

        @Override
        public void scan(String deviceId, Map<Integer, Integer> properties, Path output) throws IOException {
            scans.add(properties);
            lastOutput = output;
            if (release != null) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (Files.exists(output)) throw new IOException("WIA does not replace files");
            ImageIO.write(TestImages.rgb(6, 4), "png", output.toFile());
        }
    }

    @Test
    void listsScannersAndDropsARepeatedMaker() throws Exception {
        List<Scanner> scanners = new WiaBackend(new FakeApi()).listScanners();
        assertEquals(2, scanners.size());
        assertEquals("Canon LiDE 300", scanners.get(0).displayName());
        assertEquals("Hewlett-Packard Scanjet G3110", scanners.get(1).displayName());
    }

    @Test
    void setsIntentFirstThenResolutionAndArea() {
        ScannerOptions options = new WiaBackend(new FakeApi()).options(CANON);
        ScanSettings settings = new ScanSettings(300, ColorMode.GRAY, Optional.of(new ScanArea(25.4, 50.8, 101.6, 25.4)));
        Map<Integer, Integer> p = WiaBackend.properties(settings, options);
        assertEquals(List.of(6146, 6147, 6148, 6149, 6150, 6151, 6152), List.copyOf(p.keySet()));
        assertEquals(2, p(p, 6146));
        assertEquals(300, p(p, 6147));
        assertEquals(300, p(p, 6149));
        assertEquals(600, p(p, 6150));
        assertEquals(1200, p(p, 6151));
        assertEquals(300, p(p, 6152));
        assertEquals(List.of(6146, 6147, 6148),
                List.copyOf(WiaBackend.properties(ScanSettings.DEFAULT, options).keySet()));
    }

    @Test
    void scansIntoAFreshFileAndCleansUp() throws Exception {
        FakeApi api = new FakeApi();
        BufferedImage image = new WiaBackend(api).scan(CANON, ScanSettings.DEFAULT, ScannerOptions.FALLBACK,
                ScanMonitor.silent());
        assertEquals(6, image.getWidth());
        assertEquals(1, p(api.scans.getFirst(), 6146));
        assertFalse(Files.exists(api.lastOutput));
        assertFalse(Files.exists(api.lastOutput.getParent()));
    }

    @Test
    void cancelDoesNotWaitForTheTransfer() throws Exception {
        FakeApi api = new FakeApi();
        api.release = new CountDownLatch(1);
        ScanMonitor monitor = ScanMonitor.silent();
        Thread canceller = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
            monitor.cancel();
        });
        long start = System.nanoTime();
        assertThrows(CancellationException.class,
                () -> new WiaBackend(api).scan(CANON, ScanSettings.DEFAULT, ScannerOptions.FALLBACK, monitor));
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(3));
        canceller.join();
        api.release.countDown();
    }

    private static int p(Map<Integer, Integer> properties, int id) {
        return properties.get(id);
    }
}
