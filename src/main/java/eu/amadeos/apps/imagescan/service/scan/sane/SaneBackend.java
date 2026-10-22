package eu.amadeos.apps.imagescan.service.scan.sane;

import com.google.inject.Inject;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.ProgramResult;
import eu.amadeos.apps.imagescan.domain.ColorMode;
import eu.amadeos.apps.imagescan.domain.ScanArea;
import eu.amadeos.apps.imagescan.domain.ScanSettings;
import eu.amadeos.apps.imagescan.domain.Scanner;
import eu.amadeos.apps.imagescan.domain.ScannerOptions;
import eu.amadeos.apps.imagescan.service.image.ImageOperations;
import eu.amadeos.apps.imagescan.service.scan.ScanMonitor;
import eu.amadeos.apps.imagescan.service.scan.ScannerBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans through SANE with its command line program {@code scanimage} (Debian package {@code sane-utils}),
 * so the app needs no native library. The image comes as PNM on standard output and is decoded while it
 * arrives; {@code --progress} reports on standard error.
 */
public class SaneBackend implements ScannerBackend {
    private static final Logger LOG = LoggerFactory.getLogger(SaneBackend.class);
    public static final String PROGRAM = "scanimage";
    private static final Pattern PROGRESS = Pattern.compile("Progress:\\s*(\\d+(?:\\.\\d+)?)%");
    /** Network scanners (eSCL, WSD) can take a while to be found. */
    private static final Duration LIST_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration OPTIONS_TIMEOUT = Duration.ofSeconds(30);

    private final CommandRunner runner;

    @Inject
    public SaneBackend() {
        this(CommandRunner.system());
    }

    public SaneBackend(CommandRunner runner) {
        this.runner = runner;
    }

    @Override
    public String name() {
        return "SANE";
    }

    // --8<-- [start:list]
    @Override
    public List<Scanner> listScanners() throws IOException, InterruptedException {
        String format = String.join(SaneOptionParser.SEPARATOR, "%d", "%v", "%m", "%t") + "%n";
        ProgramResult result = runner.run(List.of(PROGRAM, "-f", format), LIST_TIMEOUT);
        if (!result.succeeded()) {
            throw new IOException(PROGRAM + " -f: " + errorText(result.stderrText(), result.exitCode()));
        }
        String list = result.stdoutText();
        List<Scanner> scanners = SaneOptionParser.parseDeviceList(list);
        if (scanners.isEmpty() && list.contains("device `")) {
            // A scanimage without -f printed the classic list instead.
            scanners = SaneOptionParser.parseLegacyList(list);
        }
        return scanners;
    }
    // --8<-- [end:list]

    @Override
    public ScannerOptions options(Scanner scanner) throws IOException, InterruptedException {
        ProgramResult result = runner.run(List.of(PROGRAM, "-d", scanner.id(), "-A"), OPTIONS_TIMEOUT);
        if (!result.succeeded()) {
            throw new IOException(PROGRAM + " -A: " + errorText(result.stderrText(), result.exitCode()));
        }
        return SaneOptionParser.parseOptions(result.stdoutText());
    }

    // --8<-- [start:scan-command]
    /** The command line for a scan; public for tests. */
    public static List<String> scanCommand(Scanner scanner, ScanSettings settings, ScannerOptions options) {
        List<String> command = new ArrayList<>(List.of(PROGRAM, "-d", scanner.id(), "--format=pnm", "--progress"));
        ColorMode mode = settings.mode();
        String modeName = options.modeName(mode)
                .or(() -> options.colorModes().stream().findFirst().flatMap(options::modeName))
                .orElse(null);
        if (modeName != null) command.add("--mode=" + modeName);
        command.add("--resolution=" + options.nearestResolution(settings.resolution()));
        settings.area().filter(a -> options.hasGlassSize()).ifPresent(area -> {
            ScanArea a = area.clampTo(options.maxWidth(), options.maxHeight());
            command.addAll(List.of("-l", mm(a.left()), "-t", mm(a.top()), "-x", mm(a.width()), "-y", mm(a.height())));
        });
        return command;
    }
    // --8<-- [end:scan-command]

    private static String mm(double value) {
        return String.format(Locale.ROOT, "%.1fmm", value);
    }

    // --8<-- [start:scan]
    @Override
    public BufferedImage scan(Scanner scanner, ScanSettings settings, ScannerOptions options, ScanMonitor monitor)
            throws IOException, InterruptedException {
        monitor.checkCancelled();
        List<String> command = scanCommand(scanner, settings, options);
        LOG.info("Scanning: {}", String.join(" ", command));
        Process process = runner.start(command);
        monitor.onCancel(process::destroy);
        process.getOutputStream().close();
        monitor.progress(ScanMonitor.UNKNOWN);
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        Thread progressReader = Thread.ofVirtual().start(() -> readProgress(process.getErrorStream(), monitor, errors));
        BufferedImage image;
        try (InputStream out = new BufferedInputStream(process.getInputStream(), 1 << 16)) {
            image = PnmReader.read(out);
            // Some backends send more than one image; only the first is wanted.
            out.transferTo(java.io.OutputStream.nullOutputStream());
        } catch (IOException e) {
            if (monitor.isCancelled()) throw new CancellationException("Scan cancelled");
            int exit = finish(process);
            progressReader.join(TimeUnit.SECONDS.toMillis(5));
            String detail = errorText(errors.toString(StandardCharsets.UTF_8), exit);
            throw new IOException(detail.isBlank() ? e.getMessage() : detail, e);
        } catch (RuntimeException e) {
            process.destroyForcibly();
            throw e;
        }
        int exit = finish(process);
        progressReader.join(TimeUnit.SECONDS.toMillis(5));
        monitor.checkCancelled();
        if (exit != 0) throw new IOException(errorText(errors.toString(StandardCharsets.UTF_8), exit));
        monitor.progress(1);
        // Backends without millimetre geometry scan the whole glass; cut the area out afterwards.
        if (settings.area().isPresent() && !options.hasGlassSize()) {
            image = cropToArea(image, settings.area().get(), options.nearestResolution(settings.resolution()));
        }
        return image;
    }
    // --8<-- [end:scan]

    static BufferedImage cropToArea(BufferedImage image, ScanArea area, int dpi) {
        double px = dpi / ScanArea.MM_PER_INCH;
        Rectangle r = new Rectangle((int) Math.round(area.left() * px), (int) Math.round(area.top() * px),
                (int) Math.round(area.width() * px), (int) Math.round(area.height() * px));
        Rectangle bounds = r.intersection(new Rectangle(image.getWidth(), image.getHeight()));
        return bounds.isEmpty() ? image : ImageOperations.crop(image, bounds);
    }

    private static int finish(Process process) throws InterruptedException {
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();
        }
        return process.exitValue();
    }

    // --8<-- [start:progress]
    /** Splits standard error at carriage returns and newlines: progress lines, everything else kept. */
    static void readProgress(InputStream err, ScanMonitor monitor, ByteArrayOutputStream other) {
        StringBuilder line = new StringBuilder();
        try (err) {
            int c;
            while ((c = err.read()) >= 0) {
                if (c == '\r' || c == '\n') {
                    handleLine(line.toString(), monitor, other);
                    line.setLength(0);
                } else {
                    line.append((char) c);
                }
            }
            handleLine(line.toString(), monitor, other);
        } catch (IOException e) {
            // The process was stopped.
        }
    }
    // --8<-- [end:progress]

    private static void handleLine(String line, ScanMonitor monitor, ByteArrayOutputStream other) {
        if (line.isBlank()) return;
        Matcher m = PROGRESS.matcher(line);
        if (m.find()) {
            monitor.progress(Double.parseDouble(m.group(1)) / 100);
        } else {
            byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
            synchronized (other) {
                other.write(bytes, 0, bytes.length);
            }
        }
    }

    /** "scanimage: open of device epson2 failed: Invalid argument", without progress noise. */
    static String errorText(String stderr, int exitCode) {
        String text = stderr.lines().map(String::strip).filter(l -> !l.isEmpty())
                .filter(l -> !PROGRESS.matcher(l).find()).reduce((a, b) -> a + "\n" + b).orElse("");
        return text.isEmpty() ? PROGRAM + " ended with exit code " + exitCode : text;
    }
}
