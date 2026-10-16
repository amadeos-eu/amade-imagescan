package org.datazuul.amadeos.imagescan.domain;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainTest {
    @Test
    void scannerShowsVendorAndModelOrItsDeviceName() {
        assertEquals("Epson Perfection V39", new Scanner("epson2:libusb:001:004", "Epson", "Perfection V39", "").displayName());
        assertEquals("airscan:e0:HP", new Scanner("airscan:e0:HP", " ", null, null).displayName());
        assertTrue(new Scanner("v4l:/dev/video0", "", "", "video camera").isCamera());
        assertFalse(new Scanner("x", "", "", "flatbed scanner").isCamera());
        assertThrows(IllegalArgumentException.class, () -> new Scanner(" ", "", "", ""));
    }

    @Test
    void areaFromPreviewPixels() {
        // 75 dpi: 75 px are one inch.
        ScanArea area = ScanArea.fromPixels(75, 150, 300, 75, 75);
        assertEquals(25.4, area.left(), 1e-9);
        assertEquals(50.8, area.top(), 1e-9);
        assertEquals(101.6, area.width(), 1e-9);
        assertEquals(25.4, area.height(), 1e-9);
        assertEquals(127.0, area.right(), 1e-9);
        assertEquals(1200L * 300, area.pixels(300));
    }

    @Test
    void areaIsCutToTheGlass() {
        ScanArea clamped = new ScanArea(200, 280, 50, 50).clampTo(215.9, 297.0);
        assertEquals(200, clamped.left(), 1e-9);
        assertEquals(15.9, clamped.width(), 1e-9);
        assertEquals(17.0, clamped.height(), 1e-9);
        // Entirely outside: the whole glass.
        ScanArea outside = new ScanArea(300, 0, 10, 10).clampTo(215.9, 297.0);
        assertEquals(215.9, outside.width(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> new ScanArea(0, 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new ScanArea(-1, 0, 10, 10));
    }

    @Test
    void nearestResolutionPrefersTheHigherOnATie() {
        ScannerOptions options = new ScannerOptions(List.of(600, 75, 300, 150), Map.of(ColorMode.COLOR, "Color"), 0, 0);
        assertEquals(List.of(75, 150, 300, 600), options.resolutions());
        assertEquals(300, options.nearestResolution(300));
        assertEquals(300, options.nearestResolution(250));
        assertEquals(150, options.nearestResolution(200));
        assertEquals(600, options.nearestResolution(4800));
        assertEquals(75, options.nearestResolution(1));
        assertFalse(options.hasGlassSize());
        assertEquals(List.of(ColorMode.COLOR), options.colorModes());
        assertEquals(Optional.empty(), options.modeName(ColorMode.GRAY));
    }

    @Test
    void settingsChangeOneThingAtATime() {
        ScanSettings s = ScanSettings.DEFAULT.withResolution(600).withMode(ColorMode.GRAY);
        assertEquals(600, s.resolution());
        assertEquals(ColorMode.GRAY, s.mode());
        assertEquals(Optional.empty(), s.area());
        assertEquals(ScanSettings.PREVIEW_RESOLUTION, ScanSettings.preview().resolution());
        assertThrows(IllegalArgumentException.class, () -> ScanSettings.DEFAULT.withResolution(0));
    }

    @Test
    void scannedImageKnowsItsSizeOnPaper() {
        ScannedImage a4 = new ScannedImage(new BufferedImage(2480, 3508, BufferedImage.TYPE_BYTE_GRAY), 300);
        assertEquals(210, Math.round(a4.widthMm()));
        assertEquals(297, Math.round(a4.heightMm()));
        ScannedImage unknown = new ScannedImage(new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_GRAY), 0);
        assertFalse(unknown.hasResolution());
        assertEquals(0, unknown.widthMm(), 0);
        assertEquals(300, a4.with(new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY)).resolution());
    }

    @Test
    void imageFormatsFromAndToFileNames() {
        assertEquals(Optional.of(ImageFormat.JPEG), ImageFormat.forPath(Path.of("Urlaub.JPG")));
        assertEquals(Optional.of(ImageFormat.TIFF), ImageFormat.forPath(Path.of("/tmp/a.tiff")));
        assertEquals(Optional.empty(), ImageFormat.forPath(Path.of("notes.txt")));
        assertEquals(Optional.empty(), ImageFormat.forPath(Path.of("scan")));
        assertEquals(Path.of("/tmp/scan.png"), ImageFormat.PNG.withExtension(Path.of("/tmp/scan")));
        assertEquals(Path.of("/tmp/scan.png"), ImageFormat.PNG.withExtension(Path.of("/tmp/scan.jpg")));
        assertEquals(Path.of("/tmp/scan.jpeg"), ImageFormat.JPEG.withExtension(Path.of("/tmp/scan.jpeg")));
        assertEquals(Path.of("/tmp/v1.2.tif"), ImageFormat.TIFF.withExtension(Path.of("/tmp/v1.2")));
    }
}
