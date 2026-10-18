package eu.amadeos.imagescan.service.print;

import eu.amadeos.imagescan.domain.ScannedImage;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImagePrintableTest {
    /** Printable area of A4 with 10 mm margins, in points. */
    private static final double X = 28.35;
    private static final double Y = 28.35;
    private static final double W = 538.6;
    private static final double H = 785.2;

    private static ScannedImage scan(int w, int h, int dpi) {
        return new ScannedImage(new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY), dpi);
    }

    @Test
    void printsAtTrueSizeCentred() {
        // A6 postcard at 300 dpi: 105 × 148 mm.
        Rectangle2D r = ImagePrintable.placement(scan(1240, 1748, 300), X, Y, W, H);
        assertEquals(1240 * 72.0 / 300, r.getWidth(), 1e-6);
        assertEquals(1748 * 72.0 / 300, r.getHeight(), 1e-6);
        assertEquals(X + (W - r.getWidth()) / 2, r.getX(), 1e-6);
    }

    @Test
    void shrinksWhatDoesNotFit() {
        // A full A4 glass does not fit inside the margins; its width decides.
        Rectangle2D r = ImagePrintable.placement(scan(2550, 3508, 300), X, Y, W, H);
        assertEquals(W, r.getWidth(), 1e-6);
        assertEquals(X, r.getX(), 1e-6);
        assertEquals(2550.0 / 3508, r.getWidth() / r.getHeight(), 1e-6);
    }

    @Test
    void fillsThePageWithoutAResolution() {
        Rectangle2D r = ImagePrintable.placement(scan(100, 50, 0), X, Y, W, H);
        assertEquals(W, r.getWidth(), 1e-6);
        assertEquals(W / 2, r.getHeight(), 1e-6);
    }
}
