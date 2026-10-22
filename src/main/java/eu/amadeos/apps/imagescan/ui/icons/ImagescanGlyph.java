package eu.amadeos.apps.imagescan.ui.icons;

import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolIcon;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * The toolbar glyphs only Imagescan needs; open, save, print, copy, undo, redo, refresh and zoom come from
 * {@link StandardGlyph}. The scanner's light and the crop area are in the theme's selection colour.
 */
public enum ImagescanGlyph implements ToolIcon.Glyph {
    PREVIEW, SCAN, CANCEL, SCANNER,
    CROP, ROTATE_LEFT, ROTATE_RIGHT, FLIP_HORIZONTAL, FLIP_VERTICAL,
    ZOOM_FIT, ZOOM_ACTUAL;

    @Override
    public void paint(ToolIcon.Pen pen) {
        switch (this) {
            case PREVIEW -> {
                scanner(pen, 1, 5.5);
                pen.magnifier(10.5, 4.5, 3.5);
            }
            case SCAN -> {
                pen.page(4.5, 1.5, 7, 7);
                scanner(pen, 1, 5);
            }
            case CANCEL -> cancel(pen);
            case SCANNER -> scanner(pen, 1, 4.5);
            case CROP -> crop(pen);
            case ROTATE_LEFT -> rotate(pen);
            case ROTATE_RIGHT -> {
                pen.mirror();
                rotate(pen);
            }
            case FLIP_HORIZONTAL -> flip(pen);
            case FLIP_VERTICAL -> {
                pen.g().rotate(Math.PI / 2, 8, 8);
                flip(pen);
            }
            case ZOOM_FIT -> zoomFit(pen);
            case ZOOM_ACTUAL -> {
                pen.magnifier(6.5, 6.5, 5);
                pen.g().setColor(pen.ink());
                pen.g().setFont(pen.g().getFont().deriveFont(Font.BOLD, 6f));
                pen.g().drawString("1:1", 3.2f, 8.6f);
            }
        }
    }

    // --8<-- [start:scanner]
    /** A flatbed seen from the front: lid, glass with the light bar, base. */
    private static void scanner(ToolIcon.Pen pen, double x, double y) {
        Path2D lid = new Path2D.Double();
        lid.moveTo(x + 0.5, y + 2.5);
        lid.lineTo(x + 2.5, y);
        lid.lineTo(x + 13.5, y);
        lid.lineTo(x + 13.5, y + 2.5);
        lid.closePath();
        pen.fillAndDraw(lid, pen.paper());
        pen.fillAndDraw(new Rectangle2D.Double(x + 0.5, y + 2.5, 13, 6), pen.paper());
        pen.fill(new Rectangle2D.Double(x + 1.5, y + 4.5, 11, 1.5), pen.light());
        pen.fillAndDraw(new Rectangle2D.Double(x + 0.5, y + 8.5, 13, 2), pen.ink());
    }
    // --8<-- [end:scanner]

    private static void cancel(ToolIcon.Pen pen) {
        pen.fillAndDraw(new Ellipse2D.Double(1.5, 1.5, 13, 13), pen.paper());
        pen.bold(2f);
        pen.line(5, 5, 11, 11);
        pen.line(11, 5, 5, 11);
        pen.thin();
    }

    /** Two crop marks: the classic L shapes. */
    private static void crop(ToolIcon.Pen pen) {
        pen.g().setStroke(new BasicStroke(1.8f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        Path2D a = new Path2D.Double();
        a.moveTo(4.5, 1);
        a.lineTo(4.5, 11.5);
        a.lineTo(15, 11.5);
        pen.draw(a);
        Path2D b = new Path2D.Double();
        b.moveTo(1, 4.5);
        b.lineTo(11.5, 4.5);
        b.lineTo(11.5, 15);
        pen.draw(b);
        pen.thin();
        pen.fill(new Rectangle2D.Double(5.5, 5.5, 5, 5), pen.light());
    }

    /** A sheet with a curved arrow over it, turning left; the right turn is its mirror image. */
    private static void rotate(ToolIcon.Pen pen) {
        pen.page(5.5, 5.5, 8, 9);
        pen.bold(1.6f);
        pen.draw(new Arc2D.Double(2, 2.5, 9, 9, 180, -100, Arc2D.OPEN));
        pen.thin();
        pen.triangle(0.5, 6, 4.5, 6, 2.5, 9.5);
    }

    /** Two triangles mirrored at a dashed axis. */
    private static void flip(ToolIcon.Pen pen) {
        Path2D left = new Path2D.Double();
        left.moveTo(6.5, 3);
        left.lineTo(6.5, 13);
        left.lineTo(1.5, 13);
        left.closePath();
        pen.fillAndDraw(left, pen.ink());
        Path2D right = new Path2D.Double();
        right.moveTo(9.5, 3);
        right.lineTo(9.5, 13);
        right.lineTo(14.5, 13);
        right.closePath();
        pen.fillAndDraw(right, pen.paper());
        pen.g().setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[]{1.5f, 1.5f}, 0));
        pen.draw(new Line2D.Double(8, 1, 8, 15));
        pen.thin();
    }

    private static void zoomFit(ToolIcon.Pen pen) {
        pen.g().setStroke(new BasicStroke(1.4f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        double[][] corners = {{1.5, 1.5, 1, 1}, {14.5, 1.5, -1, 1}, {14.5, 14.5, -1, -1}, {1.5, 14.5, 1, -1}};
        for (double[] c : corners) {
            Path2D mark = new Path2D.Double();
            mark.moveTo(c[0], c[1] + 4 * c[3]);
            mark.lineTo(c[0], c[1]);
            mark.lineTo(c[0] + 4 * c[2], c[1]);
            pen.draw(mark);
        }
        pen.thin();
        pen.fillAndDraw(new Rectangle2D.Double(4.5, 4.5, 7, 7), pen.paper());
    }
}
