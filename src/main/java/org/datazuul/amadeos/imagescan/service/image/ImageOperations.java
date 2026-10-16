package org.datazuul.amadeos.imagescan.service.image;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * Edits that keep every pixel: crop, quarter turns and mirroring. They work on the raster, so line art stays
 * one bit per pixel and grey stays grey instead of becoming RGB. The source image is
 * never changed; each operation returns a new one.
 */
public final class ImageOperations {
    private ImageOperations() {
    }

    /** The part of the image inside the rectangle, cut down to the image. */
    public static BufferedImage crop(BufferedImage source, Rectangle area) {
        Rectangle r = area.intersection(new Rectangle(source.getWidth(), source.getHeight()));
        if (r.isEmpty()) throw new IllegalArgumentException("Crop area outside the image: " + area);
        BufferedImage target = blank(source, r.width, r.height);
        Raster part = source.getRaster().createChild(r.x, r.y, r.width, r.height, 0, 0, null);
        target.getRaster().setRect(part);
        return target;
    }

    /** A quarter turn to the right. */
    public static BufferedImage rotateClockwise(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        return transform(source, h, w, (x, y) -> h - 1 - y, (x, y) -> x);
    }

    /** A quarter turn to the left. */
    public static BufferedImage rotateCounterClockwise(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        return transform(source, h, w, (x, y) -> y, (x, y) -> w - 1 - x);
    }

    /** Upside down. */
    public static BufferedImage rotate180(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        return transform(source, w, h, (x, y) -> w - 1 - x, (x, y) -> h - 1 - y);
    }

    /** Mirrored left to right. */
    public static BufferedImage flipHorizontal(BufferedImage source) {
        int w = source.getWidth();
        return transform(source, w, source.getHeight(), (x, y) -> w - 1 - x, (x, y) -> y);
    }

    /** Mirrored top to bottom. */
    public static BufferedImage flipVertical(BufferedImage source) {
        int h = source.getHeight();
        return transform(source, source.getWidth(), h, (x, y) -> x, (x, y) -> h - 1 - y);
    }

    /** An empty image of the same kind (colour model, bit depth, palette). */
    static BufferedImage blank(BufferedImage source, int width, int height) {
        ColorModel model = source.getColorModel();
        WritableRaster raster = source.getRaster().createCompatibleWritableRaster(width, height);
        return new BufferedImage(model, raster, model.isAlphaPremultiplied(), null);
    }

    /** Where the source pixel (x, y) goes in the target. */
    private interface Coordinate {
        int of(int x, int y);
    }

    private static BufferedImage transform(BufferedImage source, int width, int height, Coordinate targetX,
                                           Coordinate targetY) {
        BufferedImage target = blank(source, width, height);
        Raster in = source.getRaster();
        WritableRaster out = target.getRaster();
        Object pixel = null;
        int w = source.getWidth();
        int h = source.getHeight();
        int minX = in.getMinX();
        int minY = in.getMinY();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pixel = in.getDataElements(minX + x, minY + y, pixel);
                out.setDataElements(targetX.of(x, y), targetY.of(x, y), pixel);
            }
        }
        return target;
    }
}
