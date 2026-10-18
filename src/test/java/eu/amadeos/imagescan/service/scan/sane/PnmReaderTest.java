package eu.amadeos.imagescan.service.scan.sane;

import eu.amadeos.imagescan.TestImages;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PnmReaderTest {
    private static BufferedImage read(byte[] bytes) throws IOException {
        return PnmReader.read(new ByteArrayInputStream(bytes));
    }

    private static byte[] concat(String header, byte... data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(header.getBytes());
        out.writeBytes(data);
        return out.toByteArray();
    }

    @Test
    void readsColourInTheRightChannelOrder() throws IOException {
        BufferedImage image = read(TestImages.ppm(5, 3));
        assertEquals(BufferedImage.TYPE_3BYTE_BGR, image.getType());
        assertEquals(5, image.getWidth());
        assertEquals(3, image.getHeight());
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 5; x++) assertEquals(TestImages.rgbAt(x, y), image.getRGB(x, y), "pixel " + x + "," + y);
        }
    }

    @Test
    void readsGrey() throws IOException {
        BufferedImage image = read(concat("P5 3 1 255\n", (byte) 0, (byte) 128, (byte) 255));
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, image.getType());
        assertEquals(0, image.getRaster().getSample(0, 0, 0));
        assertEquals(128, image.getRaster().getSample(1, 0, 0));
        assertEquals(255, image.getRaster().getSample(2, 0, 0));
    }

    @Test
    void reducesSixteenBitSamples() throws IOException {
        BufferedImage image = read(concat("P5\n2 1\n65535\n", (byte) 0xff, (byte) 0xff, (byte) 0x80, (byte) 0x00));
        assertEquals(255, image.getRaster().getSample(0, 0, 0));
        assertEquals(128, image.getRaster().getSample(1, 0, 0));
    }

    @Test
    void readsLineArtWithBlackAsOne() throws IOException {
        // 10 pixels wide: two bytes per row, the last six bits are padding.
        BufferedImage image = read(concat("P4\n10 2\n", (byte) 0b1000_0000, (byte) 0b0100_0000,
                (byte) 0, (byte) 0));
        assertEquals(BufferedImage.TYPE_BYTE_BINARY, image.getType());
        assertEquals(0xff000000, image.getRGB(0, 0));
        assertEquals(0xffffffff, image.getRGB(1, 0));
        assertEquals(0xff000000, image.getRGB(9, 0));
        assertEquals(0xffffffff, image.getRGB(9, 1));
    }

    @Test
    void readsPlainFormats() throws IOException {
        BufferedImage bitmap = read("P1\n# comment\n3 1\n1 0 1\n".getBytes());
        assertEquals(0xff000000, bitmap.getRGB(0, 0));
        assertEquals(0xffffffff, bitmap.getRGB(1, 0));
        BufferedImage gray = read("P2 2 1 15 0 15".getBytes());
        assertEquals(255, gray.getRaster().getSample(1, 0, 0));
        BufferedImage rgb = read("P3 1 1 255 255 0 0".getBytes());
        assertEquals(0xffff0000, rgb.getRGB(0, 0));
    }

    @Test
    void leavesTheStreamAfterTheImage() throws IOException {
        byte[] two = concat("P5 1 1 255\n", (byte) 7, (byte) 'P', (byte) '5');
        InputStream in = new ByteArrayInputStream(two);
        PnmReader.read(in);
        assertEquals('P', in.read());
    }

    @Test
    void refusesBrokenImages() {
        assertThrows(IOException.class, () -> read("GIF89a".getBytes()));
        assertThrows(IOException.class, () -> read("P6 0 5 255\n".getBytes()));
        assertThrows(EOFException.class, () -> read(concat("P5 4 4 255\n", (byte) 1, (byte) 2)));
        assertThrows(IOException.class, () -> read("P6 100000 100000 255\n".getBytes()));
    }
}
