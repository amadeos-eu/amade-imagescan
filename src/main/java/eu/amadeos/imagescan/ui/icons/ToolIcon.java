package eu.amadeos.imagescan.ui.icons;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;

/**
 * Toolbar icons drawn in Java2D on a 16 × 16 grid, as in amade-writepad: no icon theme needed, any size, and
 * the theme's colours (lines in the button's text colour, paper in the field colour, the scanner's light in
 * the selection colour).
 */
public final class ToolIcon implements Icon {
    /** Size in toolbars at 100 % scaling. */
    public static final int SIZE = 22;

    public enum Glyph {
        OPEN, SAVE, PRINT, COPY, UNDO, REDO,
        PREVIEW, SCAN, CANCEL, SCANNER, REFRESH,
        CROP, ROTATE_LEFT, ROTATE_RIGHT, FLIP_HORIZONTAL, FLIP_VERTICAL,
        ZOOM_IN, ZOOM_OUT, ZOOM_FIT, ZOOM_ACTUAL
    }

    private final Glyph glyph;
    private final int size;

    public ToolIcon(Glyph glyph) {
        this(glyph, SIZE);
    }

    public ToolIcon(Glyph glyph, int size) {
        this.glyph = Objects.requireNonNull(glyph, "glyph");
        this.size = size;
    }

    public Glyph glyph() {
        return glyph;
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        DesktopTheme theme = AmadeUI.theme();
        boolean enabled = c == null || c.isEnabled();
        Color ink = !enabled ? theme.textInactive()
                : c != null && c.getForeground() != null ? c.getForeground() : theme.textActive();
        Color paper = theme.fieldBackground();
        Color light = enabled ? theme.selectionBackground() : theme.textInactive();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            g2.scale(size / 16.0, size / 16.0);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            new Painter(g2, ink, paper, light).paint(glyph);
        } finally {
            g2.dispose();
        }
    }

    /** Draws one glyph on the 16-unit grid; lines on half units so they are crisp at 100 %. */
    private record Painter(Graphics2D g, Color ink, Color paper, Color light) {
        void paint(Glyph glyph) {
            switch (glyph) {
                case OPEN -> open();
                case SAVE -> save();
                case PRINT -> print();
                case COPY -> {
                    page(1.5, 1.5, 8, 10);
                    page(6.5, 4.5, 8, 10);
                }
                case UNDO -> turnArrow(false);
                case REDO -> turnArrow(true);
                case PREVIEW -> {
                    scanner(1, 5.5);
                    magnifier(10.5, 4.5, 3.5);
                }
                case SCAN -> scan();
                case CANCEL -> cancel();
                case SCANNER -> scanner(1, 4.5);
                case REFRESH -> refresh();
                case CROP -> crop();
                case ROTATE_LEFT -> rotate(false);
                case ROTATE_RIGHT -> rotate(true);
                case FLIP_HORIZONTAL -> flip();
                case FLIP_VERTICAL -> {
                    g.rotate(Math.PI / 2, 8, 8);
                    flip();
                }
                case ZOOM_IN -> {
                    magnifier(6.5, 6.5, 5);
                    line(4, 6.5, 9, 6.5);
                    line(6.5, 4, 6.5, 9);
                }
                case ZOOM_OUT -> {
                    magnifier(6.5, 6.5, 5);
                    line(4, 6.5, 9, 6.5);
                }
                case ZOOM_FIT -> zoomFit();
                case ZOOM_ACTUAL -> {
                    magnifier(6.5, 6.5, 5);
                    g.setColor(ink);
                    g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 6f));
                    g.drawString("1:1", 3.2f, 8.6f);
                }
            }
        }

        private void fillAndDraw(Shape shape, Color fill) {
            g.setColor(fill);
            g.fill(shape);
            g.setColor(ink);
            g.draw(shape);
        }

        private void line(double x1, double y1, double x2, double y2) {
            g.setColor(ink);
            g.draw(new Line2D.Double(x1, y1, x2, y2));
        }

        private void page(double x, double y, double w, double h) {
            double fold = Math.min(3, w / 3);
            Path2D sheet = new Path2D.Double();
            sheet.moveTo(x, y);
            sheet.lineTo(x + w - fold, y);
            sheet.lineTo(x + w, y + fold);
            sheet.lineTo(x + w, y + h);
            sheet.lineTo(x, y + h);
            sheet.closePath();
            fillAndDraw(sheet, paper);
            Path2D corner = new Path2D.Double();
            corner.moveTo(x + w - fold, y);
            corner.lineTo(x + w - fold, y + fold);
            corner.lineTo(x + w, y + fold);
            g.setColor(ink);
            g.draw(corner);
        }

        /** A flatbed seen from the front: lid, glass with the light bar, base. */
        private void scanner(double x, double y) {
            Path2D lid = new Path2D.Double();
            lid.moveTo(x + 0.5, y + 2.5);
            lid.lineTo(x + 2.5, y);
            lid.lineTo(x + 13.5, y);
            lid.lineTo(x + 13.5, y + 2.5);
            lid.closePath();
            fillAndDraw(lid, paper);
            fillAndDraw(new Rectangle2D.Double(x + 0.5, y + 2.5, 13, 6), paper);
            g.setColor(light);
            g.fill(new Rectangle2D.Double(x + 1.5, y + 4.5, 11, 1.5));
            fillAndDraw(new Rectangle2D.Double(x + 0.5, y + 8.5, 13, 2), ink);
        }

        private void scan() {
            page(4.5, 1.5, 7, 7);
            scanner(1, 5);
        }

        private void cancel() {
            fillAndDraw(new Ellipse2D.Double(1.5, 1.5, 13, 13), paper);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            line(5, 5, 11, 11);
            line(11, 5, 5, 11);
            g.setStroke(new BasicStroke(1f));
        }

        private void refresh() {
            g.setColor(ink);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Double(2.5, 2.5, 11, 11, 60, 270, Arc2D.OPEN));
            g.setStroke(new BasicStroke(1f));
            Path2D head = new Path2D.Double();
            head.moveTo(10, 0.8);
            head.lineTo(14.8, 3.6);
            head.lineTo(10, 6.6);
            head.closePath();
            g.fill(head);
        }

        private void open() {
            Path2D back = new Path2D.Double();
            back.moveTo(1.5, 13.5);
            back.lineTo(1.5, 3.5);
            back.lineTo(5.5, 3.5);
            back.lineTo(7, 5.5);
            back.lineTo(12.5, 5.5);
            back.lineTo(12.5, 8.5);
            fillAndDraw(back, paper);
            Path2D front = new Path2D.Double();
            front.moveTo(1.5, 13.5);
            front.lineTo(4, 8.5);
            front.lineTo(15, 8.5);
            front.lineTo(12.5, 13.5);
            front.closePath();
            fillAndDraw(front, ink);
        }

        private void save() {
            Path2D disk = new Path2D.Double();
            disk.moveTo(1.5, 1.5);
            disk.lineTo(12.5, 1.5);
            disk.lineTo(14.5, 3.5);
            disk.lineTo(14.5, 14.5);
            disk.lineTo(1.5, 14.5);
            disk.closePath();
            fillAndDraw(disk, ink);
            g.setColor(paper);
            g.fill(new Rectangle2D.Double(4, 2, 7, 4));
            g.fill(new Rectangle2D.Double(3.5, 8.5, 9, 6));
            g.setColor(ink);
            g.fill(new Rectangle2D.Double(8, 2.5, 2, 3));
            line(5, 10.5, 11, 10.5);
            line(5, 12.5, 11, 12.5);
        }

        private void print() {
            fillAndDraw(new Rectangle2D.Double(4.5, 1.5, 7, 5), paper);
            fillAndDraw(new Rectangle2D.Double(1.5, 6.5, 13, 6), ink);
            fillAndDraw(new Rectangle2D.Double(4.5, 10.5, 7, 4), paper);
            g.setColor(paper);
            g.fill(new Rectangle2D.Double(11.5, 8, 1.5, 1.5));
        }

        /** Undo turns left, redo right. */
        private void turnArrow(boolean mirrored) {
            if (mirrored) {
                g.translate(16, 0);
                g.scale(-1, 1);
            }
            g.setColor(ink);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D arc = new Path2D.Double();
            arc.moveTo(5, 6.5);
            arc.lineTo(9.5, 6.5);
            arc.curveTo(13.5, 6.5, 14.5, 9.5, 14, 11);
            arc.curveTo(13.5, 13, 11.5, 13.5, 9, 13.5);
            g.draw(arc);
            g.setStroke(new BasicStroke(1f));
            Path2D head = new Path2D.Double();
            head.moveTo(1, 6.5);
            head.lineTo(6, 2.5);
            head.lineTo(6, 10.5);
            head.closePath();
            g.fill(head);
        }

        private void magnifier(double cx, double cy, double r) {
            fillAndDraw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r), paper);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double edge = r * Math.sqrt(0.5);
            line(cx + edge, cy + edge, Math.min(15, cx + r + 3.5), Math.min(15, cy + r + 3.5));
            g.setStroke(new BasicStroke(1f));
        }

        /** Two crop marks: the classic L shapes. */
        private void crop() {
            g.setColor(ink);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            Path2D a = new Path2D.Double();
            a.moveTo(4.5, 1);
            a.lineTo(4.5, 11.5);
            a.lineTo(15, 11.5);
            g.draw(a);
            Path2D b = new Path2D.Double();
            b.moveTo(1, 4.5);
            b.lineTo(11.5, 4.5);
            b.lineTo(11.5, 15);
            g.draw(b);
            g.setStroke(new BasicStroke(1f));
            g.setColor(light);
            g.fill(new Rectangle2D.Double(5.5, 5.5, 5, 5));
        }

        /** A sheet with a curved arrow over it. */
        private void rotate(boolean clockwise) {
            if (clockwise) {
                g.translate(16, 0);
                g.scale(-1, 1);
            }
            page(5.5, 5.5, 8, 9);
            g.setColor(ink);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Double(2, 2.5, 9, 9, 180, -100, Arc2D.OPEN));
            g.setStroke(new BasicStroke(1f));
            Path2D head = new Path2D.Double();
            head.moveTo(0.5, 6);
            head.lineTo(4.5, 6);
            head.lineTo(2.5, 9.5);
            head.closePath();
            g.fill(head);
        }

        /** Two triangles mirrored at a dashed axis. */
        private void flip() {
            Path2D left = new Path2D.Double();
            left.moveTo(6.5, 3);
            left.lineTo(6.5, 13);
            left.lineTo(1.5, 13);
            left.closePath();
            fillAndDraw(left, ink);
            Path2D right = new Path2D.Double();
            right.moveTo(9.5, 3);
            right.lineTo(9.5, 13);
            right.lineTo(14.5, 13);
            right.closePath();
            fillAndDraw(right, paper);
            g.setColor(ink);
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{1.5f, 1.5f}, 0));
            g.draw(new Line2D.Double(8, 1, 8, 15));
            g.setStroke(new BasicStroke(1f));
        }

        private void zoomFit() {
            g.setColor(ink);
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            double[][] corners = {{1.5, 1.5, 1, 1}, {14.5, 1.5, -1, 1}, {14.5, 14.5, -1, -1}, {1.5, 14.5, 1, -1}};
            for (double[] c : corners) {
                Path2D mark = new Path2D.Double();
                mark.moveTo(c[0], c[1] + 4 * c[3]);
                mark.lineTo(c[0], c[1]);
                mark.lineTo(c[0] + 4 * c[2], c[1]);
                g.draw(mark);
            }
            g.setStroke(new BasicStroke(1f));
            fillAndDraw(new Rectangle2D.Double(4.5, 4.5, 7, 7), paper);
        }
    }
}
