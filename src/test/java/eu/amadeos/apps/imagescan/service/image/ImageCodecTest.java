package eu.amadeos.apps.imagescan.service.image;

import eu.amadeos.apps.imagescan.TestImages;
import eu.amadeos.apps.imagescan.domain.ImageFormat;
import eu.amadeos.apps.imagescan.domain.ScannedImage;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageCodecTest {
    @Test
    void keepsTheResolutionInEveryFormat() throws IOException {
        ScannedImage scan = new ScannedImage(TestImages.rgb(30, 20), 300);
        for (ImageFormat format : ImageFormat.values()) {
            ScannedImage back = ImageCodec.decode(ImageCodec.encode(scan, format, 0.9f));
            assertEquals(30, back.width(), format.label());
            assertEquals(20, back.height(), format.label());
            assertEquals(300, back.resolution(), format.label());
        }
    }

    @Test
    void losslessFormatsKeepThePixels() throws IOException {
        ScannedImage scan = new ScannedImage(TestImages.rgb(8, 5), 150);
        for (ImageFormat format : new ImageFormat[]{ImageFormat.PNG, ImageFormat.TIFF}) {
            ImageOperationsTest.assertSamePixels(scan.image(), ImageCodec.decode(ImageCodec.encode(scan, format, 1)).image());
        }
    }

    @Test
    void lineArtStaysOneBitInPngAndTiffAndBecomesGreyInJpeg() throws IOException {
        ScannedImage page = new ScannedImage(TestImages.bitmapWithBlackPixel(40, 30, 3, 4), 600);
        for (ImageFormat format : new ImageFormat[]{ImageFormat.PNG, ImageFormat.TIFF}) {
            BufferedImage back = ImageCodec.decode(ImageCodec.encode(page, format, 1)).image();
            assertEquals(1, back.getColorModel().getPixelSize(), format.label());
            assertEquals(0xff000000, back.getRGB(3, 4), format.label());
            assertEquals(0xffffffff, back.getRGB(0, 0), format.label());
        }
        ScannedImage jpeg = ImageCodec.decode(ImageCodec.encode(page, ImageFormat.JPEG, 1));
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, jpeg.image().getType());
        assertEquals(600, jpeg.resolution());
    }

    @Test
    void jpegFlattensTransparencyOnWhite() throws IOException {
        BufferedImage argb = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        BufferedImage prepared = ImageCodec.prepare(argb, ImageFormat.JPEG);
        assertEquals(BufferedImage.TYPE_INT_RGB, prepared.getType());
        assertEquals(0xffffffff, prepared.getRGB(1, 1));
        assertTrue(ImageCodec.encode(new ScannedImage(argb, 0), ImageFormat.JPEG, 0.5f).length > 0);
    }

    @Test
    void unknownResolutionStaysUnknown() throws IOException {
        ScannedImage back = ImageCodec.decode(ImageCodec.encode(new ScannedImage(TestImages.gray(3, 3), 0), ImageFormat.PNG, 1));
        assertEquals(0, back.resolution());
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, back.image().getType());
    }

    @Test
    void refusesWhatIsNoImage() {
        assertThrows(IOException.class, () -> ImageCodec.decode("hello".getBytes()));
    }
}
