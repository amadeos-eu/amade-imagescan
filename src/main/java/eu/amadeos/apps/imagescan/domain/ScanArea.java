package eu.amadeos.apps.imagescan.domain;

/**
 * Part of the scanner glass, in millimetres from its top left corner, as SANE measures it.
 */
public record ScanArea(double left, double top, double width, double height) {
    public static final double MM_PER_INCH = 25.4;

    public ScanArea {
        if (!(left >= 0) || !(top >= 0)) throw new IllegalArgumentException("Negative origin: " + left + ", " + top);
        if (!(width > 0) || !(height > 0)) throw new IllegalArgumentException("Empty area: " + width + " × " + height);
    }

    public double right() {
        return left + width;
    }

    public double bottom() {
        return top + height;
    }

    /** This area cut down to the glass, the given maximum width and height. */
    public ScanArea clampTo(double maxWidth, double maxHeight) {
        double l = Math.min(left, maxWidth);
        double t = Math.min(top, maxHeight);
        double w = Math.min(right(), maxWidth) - l;
        double h = Math.min(bottom(), maxHeight) - t;
        if (w <= 0 || h <= 0) return new ScanArea(0, 0, maxWidth, maxHeight);
        return new ScanArea(l, t, w, h);
    }

    // --8<-- [start:from-pixels]
    /**
     * The area a rectangle of pixels covers in an image scanned with the given resolution from the top left
     * corner of the glass (a preview).
     */
    public static ScanArea fromPixels(int x, int y, int width, int height, int dpi) {
        double mmPerPixel = MM_PER_INCH / dpi;
        return new ScanArea(x * mmPerPixel, y * mmPerPixel, width * mmPerPixel, height * mmPerPixel);
    }
    // --8<-- [end:from-pixels]

    /** Size in pixels at the given resolution, as the scanner will deliver it (roughly). */
    public long pixels(int dpi) {
        double px = dpi / MM_PER_INCH;
        return Math.round(width * px) * Math.round(height * px);
    }
}
