package org.datazuul.amadeos.imagescan.service.scan.sane;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads one Netpbm image (PBM, PGM, PPM; binary P4–P6 and plain P1–P3), the format {@code scanimage}
 * writes. The JDK's ImageIO does not read these.
 *
 * <ul>
 *   <li>PBM (line art) becomes {@link BufferedImage#TYPE_BYTE_BINARY}, 1 bit per pixel;</li>
 *   <li>PGM (grey) becomes {@link BufferedImage#TYPE_BYTE_GRAY};</li>
 *   <li>PPM (colour) becomes {@link BufferedImage#TYPE_3BYTE_BGR}.</li>
 * </ul>
 * Samples with more than 8 bits (16-bit scans) are reduced to 8.
 */
public final class PnmReader {
    /** Refuses images that would need more memory than this, instead of running out of it. */
    public static final long MAX_BYTES = 1536L * 1024 * 1024;

    private final InputStream in;
    private int pushedBack = -2;

    private PnmReader(InputStream in) {
        this.in = in;
    }

    /** Reads one image; the stream stays open and positioned after it. */
    public static BufferedImage read(InputStream in) throws IOException {
        return new PnmReader(in).readImage();
    }

    private BufferedImage readImage() throws IOException {
        int p = next();
        int kind = next();
        if (p != 'P' || kind < '1' || kind > '6') throw new IOException("Not a PNM image");
        int type = kind - '0';
        boolean bitmap = type == 1 || type == 4;
        int width = readNumber();
        int height = readNumber();
        int maxValue = bitmap ? 1 : readNumber();
        if (width < 1 || height < 1) throw new IOException("Invalid image size " + width + " × " + height);
        if (maxValue < 1 || maxValue > 65535) throw new IOException("Invalid maximum sample value " + maxValue);
        int channels = (type == 3 || type == 6) ? 3 : 1;
        long bytes = bitmap ? (long) ((width + 7) / 8) * height : (long) width * height * channels;
        if (bytes > MAX_BYTES) throw new IOException("Image too large: " + width + " × " + height);
        boolean binary = type >= 4;
        // Exactly one whitespace character separates the header from binary data.
        if (binary) next();

        return switch (type) {
            case 4 -> readBitmap(width, height);
            case 1 -> readPlainBitmap(width, height);
            default -> readSamples(width, height, channels, maxValue, binary);
        };
    }

    private BufferedImage readBitmap(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        int stride = (width + 7) / 8;
        // Same packing as TYPE_BYTE_BINARY, but in PBM 1 is black while index 1 of the image is white.
        readFully(data, stride * height);
        for (int i = 0; i < data.length; i++) data[i] = (byte) ~data[i];
        return image;
    }

    private BufferedImage readPlainBitmap(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        var raster = image.getRaster();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int c = nextNonSpace();
                if (c != '0' && c != '1') throw new IOException("Invalid PBM sample");
                raster.setSample(x, y, 0, c == '1' ? 0 : 1);
            }
        }
        return image;
    }

    private BufferedImage readSamples(int width, int height, int channels, int maxValue, boolean binary)
            throws IOException {
        BufferedImage image = new BufferedImage(width, height,
                channels == 3 ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        int samples = width * height * channels;
        boolean wide = maxValue > 255;
        if (binary && !wide && maxValue == 255) {
            readFully(data, samples);
        } else {
            byte[] row = binary ? new byte[width * channels * (wide ? 2 : 1)] : null;
            int i = 0;
            for (int y = 0; y < height; y++) {
                if (binary) readFully(row, row.length);
                for (int s = 0; s < width * channels; s++) {
                    int value;
                    if (!binary) value = readNumber();
                    else if (wide) value = ((row[2 * s] & 0xff) << 8) | (row[2 * s + 1] & 0xff);
                    else value = row[s] & 0xff;
                    if (value > maxValue) value = maxValue;
                    data[i++] = (byte) Math.round(value * 255.0 / maxValue);
                }
            }
        }
        if (channels == 3) {
            // PPM is RGB, the raster of TYPE_3BYTE_BGR is BGR.
            for (int i = 0; i < samples; i += 3) {
                byte r = data[i];
                data[i] = data[i + 2];
                data[i + 2] = r;
            }
        }
        return image;
    }

    private int next() throws IOException {
        if (pushedBack != -2) {
            int c = pushedBack;
            pushedBack = -2;
            return c;
        }
        return in.read();
    }

    /** The next character that is not whitespace or part of a comment. */
    private int nextNonSpace() throws IOException {
        while (true) {
            int c = next();
            if (c < 0) throw new EOFException("Unexpected end of image");
            if (c == '#') {
                while (c != '\n' && c != '\r') {
                    c = next();
                    if (c < 0) throw new EOFException("Unexpected end of image");
                }
            } else if (!Character.isWhitespace(c)) {
                return c;
            }
        }
    }

    private int readNumber() throws IOException {
        int c = nextNonSpace();
        if (c < '0' || c > '9') throw new IOException("Number expected in PNM header");
        long value = 0;
        while (c >= '0' && c <= '9') {
            value = value * 10 + (c - '0');
            if (value > Integer.MAX_VALUE) throw new IOException("Number too large in PNM header");
            c = next();
        }
        if (c == '#') pushedBack = c;
        else if (c >= 0 && !Character.isWhitespace(c)) throw new IOException("Invalid character in PNM header");
        else if (c >= 0) pushedBack = c;
        return (int) value;
    }

    private void readFully(byte[] buffer, int length) throws IOException {
        int offset = 0;
        if (pushedBack >= 0 && length > 0) {
            buffer[offset++] = (byte) pushedBack;
            pushedBack = -2;
        }
        while (offset < length) {
            int n = in.read(buffer, offset, length - offset);
            if (n < 0) throw new EOFException("Image ends after " + offset + " of " + length + " bytes");
            offset += n;
        }
    }
}
