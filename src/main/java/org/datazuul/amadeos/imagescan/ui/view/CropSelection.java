package org.datazuul.amadeos.imagescan.ui.view;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * The frame the user draws on an image, in image pixel coordinates so that it stays on the same part of the
 * image whatever the zoom. Drawn by dragging, moved by dragging inside, resized at its eight handles; always
 * kept inside the image. Without Swing, so it can be tested.
 */
public final class CropSelection {
    /** Corners and edge midpoints, clockwise from the top left. */
    public enum Handle {
        NW, N, NE, E, SE, S, SW, W
    }

    /** What is under the mouse. */
    public enum Hit {
        NONE, INSIDE, HANDLE
    }

    private enum Drag {
        NONE, DRAWING, MOVING, RESIZING
    }

    private int imageWidth;
    private int imageHeight;
    private Rectangle2D selection;
    private Drag drag = Drag.NONE;
    private Point2D start;
    private Rectangle2D original;
    private Handle handle;

    /** A new image: the frame goes. */
    public void setImageSize(int width, int height) {
        imageWidth = width;
        imageHeight = height;
        clear();
    }

    public void clear() {
        selection = null;
        drag = Drag.NONE;
    }

    public void selectAll() {
        if (imageWidth > 0 && imageHeight > 0) selection = new Rectangle2D.Double(0, 0, imageWidth, imageHeight);
    }

    public void set(Rectangle r) {
        Rectangle2D clipped = clip(new Rectangle2D.Double(r.x, r.y, r.width, r.height));
        selection = clipped.isEmpty() ? null : clipped;
    }

    public boolean isDragging() {
        return drag != Drag.NONE;
    }

    /** The frame in whole image pixels, or empty. */
    public Optional<Rectangle> selection() {
        if (selection == null) return Optional.empty();
        int x = (int) Math.floor(selection.getMinX());
        int y = (int) Math.floor(selection.getMinY());
        int r = (int) Math.ceil(selection.getMaxX());
        int b = (int) Math.ceil(selection.getMaxY());
        Rectangle pixels = new Rectangle(x, y, r - x, b - y).intersection(new Rectangle(imageWidth, imageHeight));
        return pixels.isEmpty() ? Optional.empty() : Optional.of(pixels);
    }

    /** The frame exactly, for painting. */
    public Optional<Rectangle2D> bounds() {
        return Optional.ofNullable(selection).map(r -> (Rectangle2D) r.clone());
    }

    /** The eight handles, in image coordinates. */
    public Map<Handle, Point2D> handles() {
        Map<Handle, Point2D> points = new EnumMap<>(Handle.class);
        if (selection == null) return points;
        double l = selection.getMinX();
        double t = selection.getMinY();
        double r = selection.getMaxX();
        double b = selection.getMaxY();
        double cx = selection.getCenterX();
        double cy = selection.getCenterY();
        points.put(Handle.NW, new Point2D.Double(l, t));
        points.put(Handle.N, new Point2D.Double(cx, t));
        points.put(Handle.NE, new Point2D.Double(r, t));
        points.put(Handle.E, new Point2D.Double(r, cy));
        points.put(Handle.SE, new Point2D.Double(r, b));
        points.put(Handle.S, new Point2D.Double(cx, b));
        points.put(Handle.SW, new Point2D.Double(l, b));
        points.put(Handle.W, new Point2D.Double(l, cy));
        return points;
    }

    /** The handle within {@code tolerance} image pixels of the point. */
    public Optional<Handle> handleAt(double x, double y, double tolerance) {
        for (Map.Entry<Handle, Point2D> e : handles().entrySet()) {
            Point2D p = e.getValue();
            if (Math.abs(p.getX() - x) <= tolerance && Math.abs(p.getY() - y) <= tolerance) return Optional.of(e.getKey());
        }
        return Optional.empty();
    }

    public Hit hit(double x, double y, double tolerance) {
        if (handleAt(x, y, tolerance).isPresent()) return Hit.HANDLE;
        if (selection != null && selection.contains(x, y)) return Hit.INSIDE;
        return Hit.NONE;
    }

    /** Mouse pressed: on a handle resizes, inside moves, elsewhere starts a new frame. */
    public void press(double x, double y, double tolerance) {
        if (imageWidth <= 0 || imageHeight <= 0) return;
        start = new Point2D.Double(x, y);
        Optional<Handle> h = handleAt(x, y, tolerance);
        if (h.isPresent()) {
            drag = Drag.RESIZING;
            handle = h.get();
            original = (Rectangle2D) selection.clone();
        } else if (selection != null && selection.contains(x, y)) {
            drag = Drag.MOVING;
            original = (Rectangle2D) selection.clone();
        } else {
            drag = Drag.DRAWING;
            Point2D p = clampPoint(x, y);
            start = p;
            selection = new Rectangle2D.Double(p.getX(), p.getY(), 0, 0);
        }
    }

    public void drag(double x, double y) {
        switch (drag) {
            case DRAWING -> {
                Point2D p = clampPoint(x, y);
                selection = normalized(start.getX(), start.getY(), p.getX(), p.getY());
            }
            case MOVING -> {
                double dx = x - start.getX();
                double dy = y - start.getY();
                double nx = Math.max(0, Math.min(imageWidth - original.getWidth(), original.getX() + dx));
                double ny = Math.max(0, Math.min(imageHeight - original.getHeight(), original.getY() + dy));
                selection = new Rectangle2D.Double(nx, ny, original.getWidth(), original.getHeight());
            }
            case RESIZING -> resize(x - start.getX(), y - start.getY());
            case NONE -> {
            }
        }
    }

    /**
     * Mouse released. A frame smaller than {@code minSize} image pixels (a click) is dropped.
     */
    public void release(double minSize) {
        if (drag == Drag.NONE) return;
        if (selection != null && (selection.getWidth() < minSize || selection.getHeight() < minSize)) selection = null;
        drag = Drag.NONE;
        original = null;
        handle = null;
    }

    private void resize(double dx, double dy) {
        double l = original.getMinX();
        double t = original.getMinY();
        double r = original.getMaxX();
        double b = original.getMaxY();
        switch (handle) {
            case NW -> { l += dx; t += dy; }
            case N -> t += dy;
            case NE -> { r += dx; t += dy; }
            case E -> r += dx;
            case SE -> { r += dx; b += dy; }
            case S -> b += dy;
            case SW -> { l += dx; b += dy; }
            case W -> l += dx;
        }
        selection = clip(normalized(l, t, r, b));
    }

    private Point2D clampPoint(double x, double y) {
        return new Point2D.Double(Math.max(0, Math.min(imageWidth, x)), Math.max(0, Math.min(imageHeight, y)));
    }

    private Rectangle2D clip(Rectangle2D r) {
        Rectangle2D out = new Rectangle2D.Double();
        Rectangle2D.intersect(r, new Rectangle2D.Double(0, 0, imageWidth, imageHeight), out);
        if (out.getWidth() < 0 || out.getHeight() < 0) return new Rectangle2D.Double();
        return out;
    }

    private static Rectangle2D normalized(double x1, double y1, double x2, double y2) {
        return new Rectangle2D.Double(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
    }
}
