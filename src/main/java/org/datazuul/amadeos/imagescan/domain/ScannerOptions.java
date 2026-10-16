package org.datazuul.amadeos.imagescan.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a scanner offers.
 *
 * @param resolutions resolutions it supports, ascending; for a range only some common steps of it
 * @param modes       our colour modes, each with the backend's own name for it (e.g. COLOR → "Color")
 * @param maxWidth    width of the glass in millimetres
 * @param maxHeight   height of the glass in millimetres
 */
public record ScannerOptions(List<Integer> resolutions, Map<ColorMode, String> modes, double maxWidth,
                             double maxHeight) {
    /** Steps offered when a scanner gives its resolutions as a range. */
    public static final List<Integer> COMMON_RESOLUTIONS = List.of(75, 100, 150, 200, 300, 400, 600, 1200, 2400, 4800);

    /** When the scanner cannot be asked: what nearly all flatbeds can do, an A4 glass. */
    public static final ScannerOptions FALLBACK = new ScannerOptions(List.of(75, 150, 300, 600),
            Map.of(ColorMode.COLOR, "Color", ColorMode.GRAY, "Gray", ColorMode.LINEART, "Lineart"), 215.9, 297.0);

    public ScannerOptions {
        resolutions = resolutions.stream().distinct().sorted().toList();
        modes = Map.copyOf(modes);
        if (resolutions.isEmpty()) throw new IllegalArgumentException("No resolutions");
    }

    /** The supported resolution closest to the wish; ties go to the higher one. */
    public int nearestResolution(int wanted) {
        return resolutions.stream()
                .min(Comparator.<Integer>comparingInt(r -> Math.abs(r - wanted)).thenComparing(Comparator.reverseOrder()))
                .orElseThrow();
    }

    /** The backend's name for the mode, if the scanner has it. */
    public Optional<String> modeName(ColorMode mode) {
        return Optional.ofNullable(modes.get(Objects.requireNonNull(mode, "mode")));
    }

    /** The modes the scanner has, in the order colour, grey, line art. */
    public List<ColorMode> colorModes() {
        return java.util.Arrays.stream(ColorMode.values()).filter(modes::containsKey).toList();
    }

    public boolean hasGlassSize() {
        return maxWidth > 0 && maxHeight > 0;
    }
}
