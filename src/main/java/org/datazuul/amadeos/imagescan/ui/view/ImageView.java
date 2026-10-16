package org.datazuul.amadeos.imagescan.ui.view;

import org.datazuul.amadeos.commons.swing.AmadeUI;
import org.datazuul.amadeos.commons.swing.EdtExecutor;
import org.datazuul.amadeos.commons.theme.DesktopTheme;
import org.datazuul.amadeos.imagescan.ui.icons.ToolIcon;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Shows the scan on a dark desk, with zoom (mouse wheel at the pointer, or the zoom commands), panning (drag
 * with the right or middle button) and the crop frame (drag with the left button; handles to resize, drag
 * inside to move). Outside the frame the image is dimmed; a label gives the frame's size.
 */
public final class ImageView extends JComponent {
    private static final int HANDLE_SIZE = 8;
    /** Pointer distance from the edge at which a drag scrolls the image. */
    private static final int AUTO_SCROLL_MARGIN = 16;
    private static final int AUTO_SCROLL_STEP = 12;

    /** Told about the frame and the zoom. */
    public interface Listener {
        void selectionChanged(Optional<Rectangle> selection);

        void zoomChanged(int percent);
    }

    private final Viewport viewport = new Viewport();
    private final CropSelection crop = new CropSelection();
    private BufferedImage image;
    private Listener listener = new Listener() {
        @Override
        public void selectionChanged(Optional<Rectangle> selection) {
        }

        @Override
        public void zoomChanged(int percent) {
        }
    };
    private Function<Rectangle, String> sizeLabel = r -> r.width + " × " + r.height;
    private String badge;
    private String placeholderTitle = "";
    private String placeholderHint = "";
    private Point2D panStart;
    private boolean selecting;

    public ImageView() {
        setFocusable(true);
        setOpaque(true);
        setPreferredSize(new Dimension(640, 560));
        AmadeUI.style(this, (view, theme) -> {
            view.setBackground(theme.shadow());
            // Placeholder drawing and text, readable on the desk in light and dark themes.
            view.setForeground(DesktopTheme.isDark(theme.shadow()) ? theme.highlight() : theme.darkShadow());
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressed(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                dragged(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                released(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateCursor(e.getX(), e.getY());
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (image == null) return;
                double factor = Math.pow(Viewport.ZOOM_STEP, -e.getPreciseWheelRotation());
                viewport.zoomAt(factor, e.getX(), e.getY());
                zoomed();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                viewport.setPanelSize(getWidth(), getHeight());
                zoomed();
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Text of the label at the frame, e.g. "1240 × 1754 px · 105 × 148 mm". */
    public void setSizeLabel(Function<Rectangle, String> sizeLabel) {
        this.sizeLabel = sizeLabel;
        repaint();
    }

    /** What the empty desk says. */
    public void setPlaceholder(String title, String hint) {
        placeholderTitle = title;
        placeholderHint = hint;
        repaint();
    }

    /**
     * Shows an image, fitted, without a frame.
     *
     * @param badge marks the kind of image in the corner (e.g. "Preview"); null for none
     */
    public void setImage(BufferedImage newImage, String badge) {
        EdtExecutor.requireEdt();
        boolean sameSize = image != null && newImage != null && image.getWidth() == newImage.getWidth()
                && image.getHeight() == newImage.getHeight();
        image = newImage;
        this.badge = badge;
        viewport.setPanelSize(getWidth(), getHeight());
        if (newImage == null) {
            viewport.setImageSize(0, 0);
            crop.setImageSize(0, 0);
        } else if (!sameSize) {
            viewport.setImageSize(newImage.getWidth(), newImage.getHeight());
            crop.setImageSize(newImage.getWidth(), newImage.getHeight());
        } else {
            // Same size (undo of a flip): zoom and position stay, the frame goes.
            crop.clear();
        }
        setCursor(Cursor.getPredefinedCursor(newImage == null ? Cursor.DEFAULT_CURSOR : Cursor.CROSSHAIR_CURSOR));
        selectionChanged();
        zoomed();
    }

    public BufferedImage image() {
        return image;
    }

    public Optional<Rectangle> selection() {
        return crop.selection();
    }

    public void selectAll() {
        crop.selectAll();
        selectionChanged();
    }

    /** Sets the frame, in image pixels. */
    public void setSelection(Rectangle selection) {
        crop.set(selection);
        selectionChanged();
    }

    public void clearSelection() {
        crop.clear();
        selectionChanged();
    }

    public Viewport viewport() {
        return viewport;
    }

    public void zoomIn() {
        viewport.zoomIn();
        zoomed();
    }

    public void zoomOut() {
        viewport.zoomOut();
        zoomed();
    }

    public void zoomToFit() {
        viewport.fit();
        zoomed();
    }

    public void zoomActualSize() {
        viewport.actualSize();
        zoomed();
    }

    private void zoomed() {
        listener.zoomChanged(viewport.zoomPercent());
        repaint();
    }

    private void selectionChanged() {
        listener.selectionChanged(crop.selection());
        repaint();
    }

    // ---------------------------------------------------------------- mouse

    private double tolerance() {
        return viewport.toImageDistance(HANDLE_SIZE / 2.0 + 3);
    }

    private void pressed(MouseEvent e) {
        if (image == null) return;
        requestFocusInWindow();
        if (SwingUtilities.isRightMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) {
            panStart = e.getPoint();
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            return;
        }
        if (!SwingUtilities.isLeftMouseButton(e)) return;
        Point2D p = viewport.toImage(e.getX(), e.getY());
        crop.press(p.getX(), p.getY(), tolerance());
        selecting = true;
        repaint();
    }

    private void dragged(MouseEvent e) {
        if (panStart != null) {
            viewport.panBy(e.getX() - panStart.getX(), e.getY() - panStart.getY());
            panStart = e.getPoint();
            repaint();
            return;
        }
        if (!selecting) return;
        autoScroll(e.getX(), e.getY());
        Point2D p = viewport.toImage(e.getX(), e.getY());
        crop.drag(p.getX(), p.getY());
        repaint();
    }

    /** Dragging the frame past the edge of a zoomed image scrolls it. */
    private void autoScroll(int x, int y) {
        Rectangle2D shown = viewport.displayedImage();
        double dx = 0;
        double dy = 0;
        if (x < AUTO_SCROLL_MARGIN && shown.getMinX() < 0) dx = AUTO_SCROLL_STEP;
        else if (x > getWidth() - AUTO_SCROLL_MARGIN && shown.getMaxX() > getWidth()) dx = -AUTO_SCROLL_STEP;
        if (y < AUTO_SCROLL_MARGIN && shown.getMinY() < 0) dy = AUTO_SCROLL_STEP;
        else if (y > getHeight() - AUTO_SCROLL_MARGIN && shown.getMaxY() > getHeight()) dy = -AUTO_SCROLL_STEP;
        if (dx != 0 || dy != 0) viewport.panBy(dx, dy);
    }

    private void released(MouseEvent e) {
        if (panStart != null) {
            panStart = null;
            updateCursor(e.getX(), e.getY());
            return;
        }
        if (!selecting) return;
        selecting = false;
        // A click (less than a few screen pixels) removes the frame.
        crop.release(viewport.toImageDistance(4));
        updateCursor(e.getX(), e.getY());
        selectionChanged();
    }

    private void updateCursor(int x, int y) {
        if (image == null) {
            setCursor(Cursor.getDefaultCursor());
            return;
        }
        Point2D p = viewport.toImage(x, y);
        Optional<CropSelection.Handle> handle = crop.handleAt(p.getX(), p.getY(), tolerance());
        int cursor;
        if (handle.isPresent()) {
            cursor = switch (handle.get()) {
                case NW -> Cursor.NW_RESIZE_CURSOR;
                case N -> Cursor.N_RESIZE_CURSOR;
                case NE -> Cursor.NE_RESIZE_CURSOR;
                case E -> Cursor.E_RESIZE_CURSOR;
                case SE -> Cursor.SE_RESIZE_CURSOR;
                case S -> Cursor.S_RESIZE_CURSOR;
                case SW -> Cursor.SW_RESIZE_CURSOR;
                case W -> Cursor.W_RESIZE_CURSOR;
            };
        } else if (crop.hit(p.getX(), p.getY(), tolerance()) == CropSelection.Hit.INSIDE) {
            cursor = Cursor.MOVE_CURSOR;
        } else {
            cursor = Cursor.CROSSHAIR_CURSOR;
        }
        setCursor(Cursor.getPredefinedCursor(cursor));
    }

    // ---------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g) {
        DesktopTheme theme = AmadeUI.theme();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            viewport.setPanelSize(getWidth(), getHeight());
            if (image == null) {
                paintPlaceholder(g2, theme);
                return;
            }
            Rectangle2D shown = viewport.displayedImage();
            int x = (int) Math.round(shown.getX());
            int y = (int) Math.round(shown.getY());
            int w = (int) Math.round(shown.getWidth());
            int h = (int) Math.round(shown.getHeight());
            g2.setColor(new Color(0, 0, 0, 90));
            g2.fillRect(x + 5, y + 5, w, h);
            g2.setColor(Color.WHITE);
            g2.fillRect(x, y, w, h);
            // Smooth when reducing, sharp pixels when enlarging a lot.
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, viewport.scale() >= 2
                    ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(image, x, y, w, h, null);
            g2.setColor(theme.darkShadow());
            g2.drawRect(x - 1, y - 1, w + 1, h + 1);
            crop.bounds().filter(r -> r.getWidth() > 0 && r.getHeight() > 0)
                    .ifPresent(r -> paintFrame(g2, viewport.toPanel(r)));
            if (badge != null) paintBadge(g2, theme, badge);
        } finally {
            g2.dispose();
        }
    }

    private void paintFrame(Graphics2D g2, Rectangle2D frame) {
        Area outside = new Area(new Rectangle(0, 0, getWidth(), getHeight()));
        outside.subtract(new Area(frame));
        g2.setColor(new Color(0, 0, 0, 120));
        g2.fill(outside);

        // Marching ants: white dashes over a dark line, visible on any picture.
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(0, 0, 0, 200));
        g2.draw(frame);
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f));
        g2.setColor(Color.WHITE);
        g2.draw(frame);

        // Thirds, as a guide for straightening the frame.
        g2.setStroke(new BasicStroke(0.5f));
        g2.setColor(new Color(255, 255, 255, 60));
        for (int i = 1; i < 3; i++) {
            double fx = frame.getX() + frame.getWidth() * i / 3;
            double fy = frame.getY() + frame.getHeight() * i / 3;
            g2.draw(new java.awt.geom.Line2D.Double(fx, frame.getY(), fx, frame.getMaxY()));
            g2.draw(new java.awt.geom.Line2D.Double(frame.getX(), fy, frame.getMaxX(), fy));
        }

        g2.setStroke(new BasicStroke(1f));
        int hs = HANDLE_SIZE / 2;
        for (Map.Entry<CropSelection.Handle, Point2D> e : crop.handles().entrySet()) {
            Point2D p = viewport.toPanel(e.getValue());
            int px = (int) Math.round(p.getX());
            int py = (int) Math.round(p.getY());
            g2.setColor(Color.WHITE);
            g2.fillRect(px - hs, py - hs, HANDLE_SIZE, HANDLE_SIZE);
            g2.setColor(Color.BLACK);
            g2.drawRect(px - hs, py - hs, HANDLE_SIZE, HANDLE_SIZE);
        }

        crop.selection().ifPresent(r -> {
            String label = sizeLabel.apply(r);
            g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
            FontMetrics fm = g2.getFontMetrics();
            int lw = fm.stringWidth(label) + 10;
            int lh = fm.getHeight() + 4;
            int lx = (int) Math.round(frame.getCenterX() - lw / 2.0);
            int ly = (int) Math.round(frame.getMaxY() + 6);
            if (ly + lh > getHeight() - 4) ly = (int) Math.round(frame.getMinY() - lh - 6);
            if (ly < 4) ly = (int) Math.round(frame.getMinY() + 6);
            lx = Math.max(4, Math.min(getWidth() - lw - 4, lx));
            g2.setColor(new Color(0, 0, 0, 190));
            g2.fillRoundRect(lx, ly, lw, lh, 6, 6);
            g2.setColor(Color.WHITE);
            g2.drawString(label, lx + 5, ly + 2 + fm.getAscent());
        });
    }

    private void paintBadge(Graphics2D g2, DesktopTheme theme, String text) {
        g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(text) + 16;
        int h = fm.getHeight() + 6;
        g2.setColor(theme.selectionBackground());
        g2.fillRect(8, 8, w, h);
        AmadeUI.paintBevel(g2, 8, 8, w, h, true, 1);
        g2.setColor(theme.selectionText());
        g2.drawString(text, 16, 8 + 3 + fm.getAscent());
    }

    private void paintPlaceholder(Graphics2D g2, DesktopTheme theme) {
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        int size = 96;
        new ToolIcon(ToolIcon.Glyph.SCANNER, size).paintIcon(this, g2, cx - size / 2, cy - size + 8);
        g2.setColor(getForeground());
        g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(placeholderTitle, cx - fm.stringWidth(placeholderTitle) / 2, cy + 40);
        g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        fm = g2.getFontMetrics();
        g2.drawString(placeholderHint, cx - fm.stringWidth(placeholderHint) / 2, cy + 62);
    }
}
