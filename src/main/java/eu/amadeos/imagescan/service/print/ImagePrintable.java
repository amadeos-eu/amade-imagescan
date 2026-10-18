package eu.amadeos.imagescan.service.print;

import eu.amadeos.imagescan.domain.ScannedImage;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;

/**
 * Prints a scan on one page at its true size (a scanned A5 page comes out A5), centred in the printable
 * area. If it is larger than the paper, or its resolution is unknown, it is scaled down to fit.
 */
public final class ImagePrintable implements Printable {
    private final ScannedImage scan;

    public ImagePrintable(ScannedImage scan) {
        this.scan = scan;
    }

    /** Where the image goes on the page, in points. */
    public static Rectangle2D placement(ScannedImage scan, double x, double y, double width, double height) {
        double w;
        double h;
        if (scan.hasResolution()) {
            w = scan.width() * 72.0 / scan.resolution();
            h = scan.height() * 72.0 / scan.resolution();
        } else {
            // Only the proportions are known: fill the page.
            w = scan.width();
            h = scan.height();
        }
        double fit = Math.min(width / w, height / h);
        if (fit < 1 || !scan.hasResolution()) {
            w *= fit;
            h *= fit;
        }
        return new Rectangle2D.Double(x + (width - w) / 2, y + (height - h) / 2, w, h);
    }

    @Override
    public int print(Graphics graphics, PageFormat page, int pageIndex) {
        if (pageIndex > 0) return NO_SUCH_PAGE;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            Rectangle2D r = placement(scan, page.getImageableX(), page.getImageableY(), page.getImageableWidth(),
                    page.getImageableHeight());
            g.translate(r.getX(), r.getY());
            g.scale(r.getWidth() / scan.width(), r.getHeight() / scan.height());
            g.drawImage(scan.image(), 0, 0, null);
        } finally {
            g.dispose();
        }
        return PAGE_EXISTS;
    }
}
