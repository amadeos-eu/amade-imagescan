package eu.amadeos.imagescan.service.scan.wia;

import com.google.inject.Inject;
import eu.amadeos.imagescan.domain.ScanArea;
import eu.amadeos.imagescan.domain.ScanSettings;
import eu.amadeos.imagescan.domain.Scanner;
import eu.amadeos.imagescan.domain.ScannerOptions;
import eu.amadeos.imagescan.service.scan.ScanMonitor;
import eu.amadeos.imagescan.service.scan.ScannerBackend;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Scans on Windows through Windows Image Acquisition, which ships with every Windows and bridges TWAIN
 * drivers itself. The calls go over COM inside this process ({@link ComWiaApi}); no PowerShell or other
 * program is started, which Windows Defender would block.
 */
public class WiaBackend implements ScannerBackend {
    /** WIA item properties. */
    static final int CURRENT_INTENT = 6146;
    static final int X_RESOLUTION = 6147;
    static final int Y_RESOLUTION = 6148;
    static final int X_POSITION = 6149;
    static final int Y_POSITION = 6150;
    static final int X_EXTENT = 6151;
    static final int Y_EXTENT = 6152;

    private final WiaApi api;

    @Inject
    public WiaBackend() {
        this(new ComWiaApi());
    }

    public WiaBackend(WiaApi api) {
        this.api = api;
    }

    @Override
    public String name() {
        return "WIA";
    }

    @Override
    public List<Scanner> listScanners() throws IOException {
        List<Scanner> scanners = new ArrayList<>();
        for (WiaApi.Device d : api.devices()) {
            String model = d.name().strip();
            String vendor = d.manufacturer().strip();
            // WIA names often start with the maker already.
            if (!vendor.isEmpty() && model.toLowerCase(Locale.ROOT).startsWith(vendor.toLowerCase(Locale.ROOT))) {
                vendor = "";
            }
            scanners.add(new Scanner(d.id(), vendor, model, "flatbed scanner"));
        }
        return scanners;
    }

    /** WIA's automation library does not list the values a scanner allows; the usual ones work on nearly all. */
    @Override
    public ScannerOptions options(Scanner scanner) {
        return new ScannerOptions(List.of(75, 100, 150, 200, 300, 600, 1200), ScannerOptions.FALLBACK.modes(),
                ScannerOptions.FALLBACK.maxWidth(), ScannerOptions.FALLBACK.maxHeight());
    }

    /** The item properties for a scan, in the order they are set. */
    static Map<Integer, Integer> properties(ScanSettings settings, ScannerOptions options) {
        int dpi = options.nearestResolution(settings.resolution());
        Map<Integer, Integer> properties = new LinkedHashMap<>();
        // The intent first: it resets resolution and area to the defaults of that kind of picture.
        properties.put(CURRENT_INTENT, switch (settings.mode()) {
            case COLOR -> 1;
            case GRAY -> 2;
            case LINEART -> 4;
        });
        properties.put(X_RESOLUTION, dpi);
        properties.put(Y_RESOLUTION, dpi);
        settings.area().ifPresent(area -> {
            ScanArea a = area.clampTo(options.maxWidth(), options.maxHeight());
            double px = dpi / ScanArea.MM_PER_INCH;
            properties.put(X_POSITION, (int) Math.round(a.left() * px));
            properties.put(Y_POSITION, (int) Math.round(a.top() * px));
            properties.put(X_EXTENT, (int) Math.round(a.width() * px));
            properties.put(Y_EXTENT, (int) Math.round(a.height() * px));
        });
        return properties;
    }

    /**
     * Scans. WIA's transfer cannot be interrupted: on cancel the result is dropped and the scanner finishes on
     * its own.
     */
    @Override
    public BufferedImage scan(Scanner scanner, ScanSettings settings, ScannerOptions options, ScanMonitor monitor)
            throws IOException {
        monitor.checkCancelled();
        // WIA refuses to replace a file, so the name must not exist yet.
        Path dir = Files.createTempDirectory("amade-imagescan");
        Path output = dir.resolve("scan.png");
        monitor.progress(ScanMonitor.UNKNOWN);
        CompletableFuture<Void> transfer = CompletableFuture.runAsync(() -> {
            try {
                api.scan(scanner.id(), properties(settings, options), output);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, r -> Thread.ofVirtual().start(r));
        monitor.onCancel(() -> transfer.cancel(false));
        boolean finished = false;
        try {
            transfer.join();
            finished = true;
            BufferedImage image = ImageIO.read(output.toFile());
            if (image == null) throw new IOException("WIA delivered no readable image");
            monitor.progress(1);
            return image;
        } catch (CancellationException e) {
            // The transfer goes on in the background; its file goes when the program ends.
            output.toFile().deleteOnExit();
            dir.toFile().deleteOnExit();
            throw new CancellationException("Scan cancelled");
        } catch (CompletionException e) {
            finished = true;
            if (e.getCause() instanceof IOException io) throw io;
            throw e;
        } finally {
            if (finished) {
                Files.deleteIfExists(output);
                Files.deleteIfExists(dir);
            }
        }
    }
}
