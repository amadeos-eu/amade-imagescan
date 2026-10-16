package org.datazuul.amadeos.imagescan.domain;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * An image with the resolution it was scanned at, so that it is saved and printed at its true size.
 *
 * @param image      the pixels
 * @param resolution dots per inch; 0 if unknown (an opened file without that information)
 */
public record ScannedImage(BufferedImage image, int resolution) {
    public ScannedImage {
        Objects.requireNonNull(image, "image");
        if (resolution < 0) throw new IllegalArgumentException("Resolution: " + resolution);
    }

    public int width() {
        return image.getWidth();
    }

    public int height() {
        return image.getHeight();
    }

    public boolean hasResolution() {
        return resolution > 0;
    }

    /** The same resolution with other pixels, after an edit. */
    public ScannedImage with(BufferedImage newImage) {
        return new ScannedImage(newImage, resolution);
    }

    /** Width in millimetres; 0 if the resolution is unknown. */
    public double widthMm() {
        return hasResolution() ? width() * ScanArea.MM_PER_INCH / resolution : 0;
    }

    /** Height in millimetres; 0 if the resolution is unknown. */
    public double heightMm() {
        return hasResolution() ? height() * ScanArea.MM_PER_INCH / resolution : 0;
    }
}
