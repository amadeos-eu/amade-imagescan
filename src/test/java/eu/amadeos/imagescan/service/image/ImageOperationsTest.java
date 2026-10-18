package eu.amadeos.imagescan.service.image;

import eu.amadeos.imagescan.TestImages;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static eu.amadeos.imagescan.TestImages.rgbAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageOperationsTest {
    private final BufferedImage source = TestImages.rgb(4, 3);

    @Test
    void rotatesClockwise() {
        BufferedImage r = ImageOperations.rotateClockwise(source);
        assertEquals(3, r.getWidth());
        assertEquals(4, r.getHeight());
        // The left column becomes the top row, read from bottom to top.
        assertEquals(rgbAt(0, 2), r.getRGB(0, 0));
        assertEquals(rgbAt(0, 0), r.getRGB(2, 0));
        assertEquals(rgbAt(3, 2), r.getRGB(0, 3));
    }

    @Test
    void rotatesCounterClockwise() {
        BufferedImage r = ImageOperations.rotateCounterClockwise(source);
        assertEquals(3, r.getWidth());
        assertEquals(rgbAt(3, 0), r.getRGB(0, 0));
        assertEquals(rgbAt(0, 0), r.getRGB(0, 3));
    }

    @Test
    void fourQuarterTurnsAndTwoFlipsGiveTheOriginal() {
        BufferedImage turned = source;
        for (int i = 0; i < 4; i++) turned = ImageOperations.rotateClockwise(turned);
        assertSamePixels(source, turned);
        assertSamePixels(source, ImageOperations.flipHorizontal(ImageOperations.flipHorizontal(source)));
        assertSamePixels(ImageOperations.rotate180(source),
                ImageOperations.flipVertical(ImageOperations.flipHorizontal(source)));
        assertSamePixels(source, ImageOperations.rotateCounterClockwise(ImageOperations.rotateClockwise(source)));
    }

    @Test
    void flips() {
        assertEquals(rgbAt(3, 0), ImageOperations.flipHorizontal(source).getRGB(0, 0));
        assertEquals(rgbAt(0, 2), ImageOperations.flipVertical(source).getRGB(0, 0));
    }

    @Test
    void cropsAnIndependentCopy() {
        BufferedImage c = ImageOperations.crop(source, new Rectangle(1, 1, 2, 5));
        assertEquals(2, c.getWidth());
        assertEquals(2, c.getHeight());
        assertEquals(rgbAt(1, 1), c.getRGB(0, 0));
        c.setRGB(0, 0, 0xff123456);
        assertEquals(rgbAt(1, 1), source.getRGB(1, 1));
        assertThrows(IllegalArgumentException.class, () -> ImageOperations.crop(source, new Rectangle(10, 10, 2, 2)));
    }

    @Test
    void keepsLineArtAndGrey() {
        BufferedImage bitmap = TestImages.bitmapWithBlackPixel(9, 5, 8, 0);
        BufferedImage turned = ImageOperations.rotateClockwise(bitmap);
        assertEquals(BufferedImage.TYPE_BYTE_BINARY, turned.getType());
        assertEquals(1, turned.getColorModel().getPixelSize());
        assertEquals(0xff000000, turned.getRGB(4, 8));
        assertEquals(0xffffffff, turned.getRGB(0, 0));

        BufferedImage gray = TestImages.gray(3, 2);
        BufferedImage cropped = ImageOperations.crop(gray, new Rectangle(1, 0, 2, 2));
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, cropped.getType());
        assertEquals(11, cropped.getRaster().getSample(0, 1, 0));
        BufferedImage mirrored = ImageOperations.flipHorizontal(gray);
        assertNotSame(gray, mirrored);
        assertEquals(20, mirrored.getRaster().getSample(0, 0, 0));
    }

    static void assertSamePixels(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), "pixel " + x + "," + y);
            }
        }
    }
}
