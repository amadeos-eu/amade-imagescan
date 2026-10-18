package eu.amadeos.apps.imagescan.repository;

import eu.amadeos.apps.imagescan.domain.ColorMode;
import eu.amadeos.apps.imagescan.domain.ImageFormat;
import eu.amadeos.apps.imagescan.repository.ImagescanPreferences.Bar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryTest {
    @TempDir
    Path dir;

    @Test
    void preferencesHaveDefaultsAndAreStoredAtOnce() {
        Path file = dir.resolve("imagescan.properties");
        ImagescanPreferences prefs = new ImagescanPreferences(file);
        assertEquals(Optional.empty(), prefs.scannerId());
        assertEquals(300, prefs.resolution());
        assertEquals(ColorMode.COLOR, prefs.mode());
        assertEquals(ImageFormat.PNG, prefs.format());
        assertEquals(0.9f, prefs.jpegQuality(), 1e-6);
        assertTrue(prefs.isVisible(Bar.SETTINGS));

        prefs.setScannerId("epson2:libusb:001:004");
        prefs.setResolution(600);
        prefs.setMode(ColorMode.LINEART);
        prefs.setFormat(ImageFormat.TIFF);
        prefs.setVisible(Bar.TOOLBAR, false);

        ImagescanPreferences again = new ImagescanPreferences(file);
        assertEquals(Optional.of("epson2:libusb:001:004"), again.scannerId());
        assertEquals(600, again.resolution());
        assertEquals(ColorMode.LINEART, again.mode());
        assertEquals(ImageFormat.TIFF, again.format());
        assertFalse(again.isVisible(Bar.TOOLBAR));
    }

    @Test
    void brokenValuesFallBackToTheDefaults() throws IOException {
        Path file = dir.resolve("imagescan.properties");
        Files.writeString(file, "resolution=lots\nmode=sepia\nformat=gif\njpeg.quality=500\n");
        ImagescanPreferences prefs = new ImagescanPreferences(file);
        assertEquals(300, prefs.resolution());
        assertEquals(ColorMode.COLOR, prefs.mode());
        assertEquals(ImageFormat.PNG, prefs.format());
        assertEquals(0.9f, prefs.jpegQuality(), 1e-6);
    }

    @Test
    void writesAtomicallyAndKeepsPermissions() throws IOException {
        ImageFiles files = new ImageFiles();
        Path image = dir.resolve("scan.png");
        files.write(image, new byte[]{1, 2, 3});
        assertEquals(3, files.read(image).length);
        try {
            assertEquals("rw-r--r--", PosixFilePermissions.toString(Files.getPosixFilePermissions(image)));
            Files.setPosixFilePermissions(image, PosixFilePermissions.fromString("rw-------"));
            files.write(image, new byte[]{4});
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(image)));
        } catch (UnsupportedOperationException e) {
            // Not POSIX (Windows).
        }
        assertEquals(4, files.read(image)[0]);
        try (var list = Files.list(dir)) {
            assertEquals(1, list.count(), "no temporary files left");
        }
    }
}
