package eu.amadeos.imagescan.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * How to scan.
 *
 * @param resolution dots per inch
 * @param mode       colour, grey or line art
 * @param area       part of the glass; empty for all of it
 */
public record ScanSettings(int resolution, ColorMode mode, Optional<ScanArea> area) {
    /** Resolution of the preview of the whole glass: quick, and enough to frame the part to scan. */
    public static final int PREVIEW_RESOLUTION = 75;
    public static final ScanSettings DEFAULT = new ScanSettings(300, ColorMode.COLOR, Optional.empty());

    public ScanSettings {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(area, "area");
        if (resolution < 1 || resolution > 19200) throw new IllegalArgumentException("Resolution: " + resolution);
    }

    public ScanSettings withResolution(int newResolution) {
        return new ScanSettings(newResolution, mode, area);
    }

    public ScanSettings withMode(ColorMode newMode) {
        return new ScanSettings(resolution, newMode, area);
    }

    public ScanSettings withArea(Optional<ScanArea> newArea) {
        return new ScanSettings(resolution, mode, newArea);
    }

    /** The whole glass, in colour, at the preview resolution. */
    public static ScanSettings preview() {
        return new ScanSettings(PREVIEW_RESOLUTION, ColorMode.COLOR, Optional.empty());
    }
}
