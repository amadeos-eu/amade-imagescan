package eu.amadeos.apps.imagescan.ui.view;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Where an image sits in a panel: zoom and scroll position, without Swing. Starts fitted into the panel
 * (and stays fitted when the panel is resized) until it is zoomed or panned. An image smaller than the panel
 * in one direction is centred in that direction; a larger one cannot be dragged out of sight.
 */
public final class Viewport {
    public static final double MIN_SCALE = 0.02;
    public static final double MAX_SCALE = 16;
    public static final double ZOOM_STEP = 1.25;

    private int imageWidth;
    private int imageHeight;
    private int panelWidth;
    private int panelHeight;
    private boolean fit = true;
    private double scale = 1;
    private double offsetX;
    private double offsetY;

    public void setImageSize(int width, int height) {
        imageWidth = Math.max(0, width);
        imageHeight = Math.max(0, height);
        fit = true;
    }

    /** Keeps the image fitted, or keeps its top left corner where it was. */
    public void setPanelSize(int width, int height) {
        panelWidth = Math.max(0, width);
        panelHeight = Math.max(0, height);
    }

    public boolean hasImage() {
        return imageWidth > 0 && imageHeight > 0;
    }

    public boolean isFit() {
        return fit;
    }

    /** Screen pixels per image pixel. */
    public double scale() {
        return fit ? fitScale() : scale;
    }

    /** The scale at which the whole image just fits the panel. */
    public double fitScale() {
        if (!hasImage() || panelWidth == 0 || panelHeight == 0) return 1;
        double s = Math.min((double) panelWidth / imageWidth, (double) panelHeight / imageHeight);
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
    }

    public int zoomPercent() {
        return (int) Math.round(scale() * 100);
    }

    public void fit() {
        fit = true;
    }

    /** One image pixel per screen pixel, centred on the middle of the panel. */
    public void actualSize() {
        setScaleAt(1, panelWidth / 2.0, panelHeight / 2.0);
    }

    public void zoomIn() {
        zoomAt(ZOOM_STEP, panelWidth / 2.0, panelHeight / 2.0);
    }

    public void zoomOut() {
        zoomAt(1 / ZOOM_STEP, panelWidth / 2.0, panelHeight / 2.0);
    }

    /** Zooms by the factor, keeping the image point under (px, py) where it is. */
    public void zoomAt(double factor, double px, double py) {
        setScaleAt(scale() * factor, px, py);
    }

    private void setScaleAt(double newScale, double px, double py) {
        if (!hasImage()) return;
        double clamped = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
        Point2D anchor = toImage(px, py);
        scale = clamped;
        fit = false;
        offsetX = px - anchor.getX() * scale;
        offsetY = py - anchor.getY() * scale;
    }

    /** Moves the image by screen pixels. */
    public void panBy(double dx, double dy) {
        if (!hasImage()) return;
        if (fit) {
            scale = fitScale();
            offsetX = originX();
            offsetY = originY();
            fit = false;
        }
        offsetX = originX() + dx;
        offsetY = originY() + dy;
    }

    /** Left edge of the image in the panel. */
    public double originX() {
        return origin(fit ? Double.NaN : offsetX, imageWidth * scale(), panelWidth);
    }

    /** Top edge of the image in the panel. */
    public double originY() {
        return origin(fit ? Double.NaN : offsetY, imageHeight * scale(), panelHeight);
    }

    private static double origin(double offset, double displayed, int panel) {
        if (Double.isNaN(offset) || displayed <= panel) return (panel - displayed) / 2;
        return Math.max(panel - displayed, Math.min(0, offset));
    }

    /** The image on screen. */
    public Rectangle2D displayedImage() {
        return new Rectangle2D.Double(originX(), originY(), imageWidth * scale(), imageHeight * scale());
    }

    /** A panel point in image pixel coordinates (may lie outside the image). */
    public Point2D toImage(double px, double py) {
        double s = scale();
        return new Point2D.Double((px - originX()) / s, (py - originY()) / s);
    }

    /** An image rectangle on screen. */
    public Rectangle2D toPanel(Rectangle2D image) {
        double s = scale();
        return new Rectangle2D.Double(originX() + image.getX() * s, originY() + image.getY() * s,
                image.getWidth() * s, image.getHeight() * s);
    }

    public Point2D toPanel(Point2D image) {
        double s = scale();
        return new Point2D.Double(originX() + image.getX() * s, originY() + image.getY() * s);
    }

    /** Image pixels a screen distance covers, for hit tolerances. */
    public double toImageDistance(double screen) {
        return screen / scale();
    }

    /** The whole image in its own coordinates. */
    public Rectangle imageBounds() {
        return new Rectangle(imageWidth, imageHeight);
    }
}
