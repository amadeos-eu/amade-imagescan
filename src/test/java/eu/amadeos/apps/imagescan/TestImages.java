package eu.amadeos.apps.imagescan;

import java.awt.image.BufferedImage;

/** Small images with a different value in every pixel, so that turned or mirrored pixels can be told apart. */
public final class TestImages {
    private TestImages() {
    }

    /** RGB where pixel (x, y) has the colour {@code (x * 16, y * 16, x + y)}. */
    public static BufferedImage rgb(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) image.setRGB(x, y, rgbAt(x, y));
        }
        return image;
    }

    public static int rgbAt(int x, int y) {
        return 0xff000000 | ((x * 16) & 0xff) << 16 | ((y * 16) & 0xff) << 8 | ((x + y) & 0xff);
    }

    /** Grey where pixel (x, y) is {@code x * 10 + y}. */
    public static BufferedImage gray(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) image.getRaster().setSample(x, y, 0, x * 10 + y);
        }
        return image;
    }

    /** Line art with only the pixel (x0, y0) black. */
    public static BufferedImage bitmapWithBlackPixel(int width, int height, int x0, int y0) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) image.getRaster().setSample(x, y, 0, (x == x0 && y == y0) ? 0 : 1);
        }
        return image;
    }

    /** A PPM (P6) of the {@link #rgb} image. */
    public static byte[] ppm(int width, int height) {
        byte[] header = ("P6\n# test\n" + width + " " + height + "\n255\n").getBytes();
        byte[] data = new byte[header.length + width * height * 3];
        System.arraycopy(header, 0, data, 0, header.length);
        int i = header.length;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = rgbAt(x, y);
                data[i++] = (byte) (rgb >> 16);
                data[i++] = (byte) (rgb >> 8);
                data[i++] = (byte) rgb;
            }
        }
        return data;
    }
}
