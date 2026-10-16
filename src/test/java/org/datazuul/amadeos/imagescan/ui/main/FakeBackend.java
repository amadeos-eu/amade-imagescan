package org.datazuul.amadeos.imagescan.ui.main;

import org.datazuul.amadeos.imagescan.TestImages;
import org.datazuul.amadeos.imagescan.domain.ColorMode;
import org.datazuul.amadeos.imagescan.domain.ScanSettings;
import org.datazuul.amadeos.imagescan.domain.Scanner;
import org.datazuul.amadeos.imagescan.domain.ScannerOptions;
import org.datazuul.amadeos.imagescan.service.scan.ScanMonitor;
import org.datazuul.amadeos.imagescan.service.scan.ScannerBackend;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A scanner with an A4 glass that "scans" a test image of the size SANE would deliver for the settings, so
 * that areas and resolutions can be checked.
 */
final class FakeBackend implements ScannerBackend {
    static final Scanner EPSON = new Scanner("epson2:libusb:001:004", "Epson", "Perfection V39", "flatbed scanner");
    static final Scanner CAMERA = new Scanner("v4l:/dev/video0", "Noname", "Camera", "video camera");
    static final ScannerOptions OPTIONS = new ScannerOptions(List.of(75, 150, 300, 600),
            Map.of(ColorMode.COLOR, "Color", ColorMode.GRAY, "Gray", ColorMode.LINEART, "Lineart"), 215.9, 297.0);

    List<Scanner> scanners = List.of(CAMERA, EPSON);
    ScannerOptions options = OPTIONS;
    IOException listFailure;
    IOException scanFailure;
    final List<ScanSettings> scans = new ArrayList<>();

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public List<Scanner> listScanners() throws IOException {
        if (listFailure != null) throw listFailure;
        return scanners;
    }

    @Override
    public ScannerOptions options(Scanner scanner) {
        return options;
    }

    @Override
    public BufferedImage scan(Scanner scanner, ScanSettings settings, ScannerOptions options, ScanMonitor monitor)
            throws IOException {
        scans.add(settings);
        monitor.progress(0.5);
        monitor.checkCancelled();
        if (scanFailure != null) throw scanFailure;
        int dpi = options.nearestResolution(settings.resolution());
        double width = settings.area().map(a -> a.width()).orElse(options.maxWidth());
        double height = settings.area().map(a -> a.height()).orElse(options.maxHeight());
        return TestImages.rgb((int) Math.round(width * dpi / 25.4), (int) Math.round(height * dpi / 25.4));
    }
}
