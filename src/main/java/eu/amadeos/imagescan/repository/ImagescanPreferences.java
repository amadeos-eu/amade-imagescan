package eu.amadeos.imagescan.repository;

import com.google.inject.Inject;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.imagescan.domain.ColorMode;
import eu.amadeos.imagescan.domain.ImageFormat;
import eu.amadeos.imagescan.domain.ScanSettings;
import eu.amadeos.imagescan.service.image.ImageCodec;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Settings of amade-imagescan in {@code ~/.config/amadeos/imagescan/imagescan.properties}: the scanner,
 * resolution and mode last used, the file type to save in, JPEG quality, and which bars the window shows.
 * Each change is written at once.
 */
public class ImagescanPreferences {
    public static final String FILE = "imagescan.properties";

    public enum Bar {
        TOOLBAR, SETTINGS, STATUS_BAR;

        String key() {
            return "show." + name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    private final Path file;
    private final Properties properties;

    @Inject
    public ImagescanPreferences(AppDirectories directories) {
        this(directories.configFile(FILE));
    }

    public ImagescanPreferences(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.properties = PropertiesFile.load(file);
    }

    public Optional<String> scannerId() {
        return Optional.ofNullable(properties.getProperty("scanner")).filter(s -> !s.isBlank());
    }

    public void setScannerId(String id) {
        set("scanner", id);
    }

    public int resolution() {
        return intValue("resolution", ScanSettings.DEFAULT.resolution(), 1, 19200);
    }

    public void setResolution(int dpi) {
        set("resolution", String.valueOf(dpi));
    }

    public ColorMode mode() {
        return enumValue("mode", ColorMode.class, ScanSettings.DEFAULT.mode());
    }

    public void setMode(ColorMode mode) {
        set("mode", mode.name());
    }

    public ImageFormat format() {
        return enumValue("format", ImageFormat.class, ImageFormat.PNG);
    }

    public void setFormat(ImageFormat format) {
        set("format", format.name());
    }

    /** 0.05 to 1. */
    public float jpegQuality() {
        int percent = intValue("jpeg.quality", Math.round(ImageCodec.DEFAULT_JPEG_QUALITY * 100), 5, 100);
        return percent / 100f;
    }

    public boolean isVisible(Bar bar) {
        return !"false".equals(properties.getProperty(bar.key()));
    }

    public void setVisible(Bar bar, boolean visible) {
        set(bar.key(), String.valueOf(visible));
    }

    private int intValue(String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(properties.getProperty(key, "").strip());
            return value < min || value > max ? fallback : value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private <E extends Enum<E>> E enumValue(String key, Class<E> type, E fallback) {
        String value = properties.getProperty(key);
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * Stores the value at once.
     *
     * @throws java.io.UncheckedIOException if the file cannot be written
     */
    private void set(String key, String value) {
        if (value.equals(properties.getProperty(key))) return;
        properties.setProperty(key, value);
        PropertiesFile.store(file, properties, "amade-imagescan");
    }
}
