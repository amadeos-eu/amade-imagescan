package eu.amadeos.apps.imagescan.service.image;

import eu.amadeos.apps.imagescan.domain.ImageFormat;
import eu.amadeos.apps.imagescan.domain.ScanArea;
import eu.amadeos.apps.imagescan.domain.ScannedImage;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataFormatImpl;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Turns images into PNG, JPEG or TIFF bytes and back, keeping the resolution in the file (PNG {@code pHYs},
 * JFIF density, TIFF resolution tags) so that other programs print the scan at its true size. The resolution
 * of a file is read from ImageIO's format-neutral metadata, so that other programs' files are understood too.
 */
public final class ImageCodec {
    /** JPEG quality when none is set: little visible loss at a third of the size of 1.0. */
    public static final float DEFAULT_JPEG_QUALITY = 0.9f;
    private static final String PNG_NATIVE = "javax_imageio_png_1.0";
    private static final String JPEG_NATIVE = "javax_imageio_jpeg_image_1.0";

    private ImageCodec() {
    }

    public static byte[] encode(ScannedImage scan, ImageFormat format, float jpegQuality) throws IOException {
        BufferedImage image = prepare(scan.image(), format);
        ImageWriter writer = writer(format);
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (format == ImageFormat.JPEG) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0.05f, Math.min(1f, jpegQuality)));
            } else if (format == ImageFormat.TIFF) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                boolean bilevel = image.getColorModel().getPixelSize() == 1;
                String wanted = bilevel ? "CCITT T.6" : "Deflate";
                String type = Arrays.asList(param.getCompressionTypes()).contains(wanted) ? wanted : "LZW";
                param.setCompressionType(type);
            }
            IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param);
            if (scan.hasResolution()) setResolution(metadata, format, scan.resolution());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(out);
                writer.write(null, new IIOImage(image, null, metadata), param);
            }
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    /**
     * Reads an image with its resolution (0 if the file does not say).
     *
     * @throws IOException if no ImageIO reader understands the bytes
     */
    public static ScannedImage decode(byte[] bytes) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IOException("Unknown image format");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, false);
                BufferedImage image = reader.read(0);
                int dpi = 0;
                try {
                    IIOMetadata metadata = reader.getImageMetadata(0);
                    if (metadata != null) dpi = resolution(metadata);
                } catch (IOException | RuntimeException e) {
                    // Broken metadata: the pixels are what matters.
                }
                return new ScannedImage(image, dpi);
            } finally {
                reader.dispose();
            }
        }
    }

    /** JPEG has neither one-bit pixels nor transparency: line art becomes grey, transparency white. */
    static BufferedImage prepare(BufferedImage image, ImageFormat format) {
        if (format != ImageFormat.JPEG) return image;
        boolean gray = image.getType() == BufferedImage.TYPE_BYTE_GRAY;
        boolean bilevel = image.getColorModel().getPixelSize() == 1;
        if (gray) return image;
        if (!bilevel && !image.getColorModel().hasAlpha() && image.getType() != BufferedImage.TYPE_BYTE_INDEXED
                && image.getType() != BufferedImage.TYPE_CUSTOM) {
            return image;
        }
        int type = bilevel ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB;
        BufferedImage flat = new BufferedImage(image.getWidth(), image.getHeight(), type);
        Graphics2D g = flat.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, flat.getWidth(), flat.getHeight());
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return flat;
    }

    private static ImageWriter writer(ImageFormat format) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format.imageIoName());
        if (!writers.hasNext()) throw new IOException("No writer for " + format.label());
        return writers.next();
    }

    /**
     * Writes the resolution where each format keeps it. PNG and JPEG get their own entries ({@code pHYs},
     * the JFIF density): the JDK's PNG writer rounds the neutral pixel size to a few pixels per metre, and its
     * JPEG writer ignores it. TIFF takes the neutral form.
     */
    static void setResolution(IIOMetadata metadata, ImageFormat format, int dpi) throws IIOInvalidTreeException {
        if (metadata.isReadOnly()) return;
        switch (format) {
            case PNG -> {
                String perMetre = Long.toString(Math.round(dpi / ScanArea.MM_PER_INCH * 1000));
                IIOMetadataNode phys = new IIOMetadataNode("pHYs");
                phys.setAttribute("pixelsPerUnitXAxis", perMetre);
                phys.setAttribute("pixelsPerUnitYAxis", perMetre);
                phys.setAttribute("unitSpecifier", "meter");
                IIOMetadataNode root = new IIOMetadataNode(PNG_NATIVE);
                root.appendChild(phys);
                metadata.mergeTree(PNG_NATIVE, root);
            }
            case JPEG -> {
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(JPEG_NATIVE);
                org.w3c.dom.NodeList jfif = root.getElementsByTagName("app0JFIF");
                if (jfif.getLength() == 0) return;
                IIOMetadataNode node = (IIOMetadataNode) jfif.item(0);
                node.setAttribute("resUnits", "1");
                node.setAttribute("Xdensity", Integer.toString(Math.min(65535, dpi)));
                node.setAttribute("Ydensity", Integer.toString(Math.min(65535, dpi)));
                metadata.setFromTree(JPEG_NATIVE, root);
            }
            case TIFF -> {
                if (!metadata.isStandardMetadataFormatSupported()) return;
                String size = Double.toString(ScanArea.MM_PER_INCH / dpi);
                IIOMetadataNode horizontal = new IIOMetadataNode("HorizontalPixelSize");
                horizontal.setAttribute("value", size);
                IIOMetadataNode vertical = new IIOMetadataNode("VerticalPixelSize");
                vertical.setAttribute("value", size);
                IIOMetadataNode dimension = new IIOMetadataNode("Dimension");
                dimension.appendChild(horizontal);
                dimension.appendChild(vertical);
                IIOMetadataNode root = new IIOMetadataNode(IIOMetadataFormatImpl.standardMetadataFormatName);
                root.appendChild(dimension);
                metadata.mergeTree(IIOMetadataFormatImpl.standardMetadataFormatName, root);
            }
        }
    }

    /** Dots per inch from the neutral metadata, rounded; 0 if not given. */
    static int resolution(IIOMetadata metadata) {
        if (!metadata.isStandardMetadataFormatSupported()) return 0;
        Node root = metadata.getAsTree(IIOMetadataFormatImpl.standardMetadataFormatName);
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!"Dimension".equals(n.getNodeName())) continue;
            for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) {
                if (!"HorizontalPixelSize".equals(c.getNodeName())) continue;
                NamedNodeMap attributes = c.getAttributes();
                Node value = attributes == null ? null : attributes.getNamedItem("value");
                if (value == null) return 0;
                try {
                    double mmPerPixel = Double.parseDouble(value.getNodeValue());
                    if (!(mmPerPixel > 0)) return 0;
                    long dpi = Math.round(ScanArea.MM_PER_INCH / mmPerPixel);
                    return dpi > 0 && dpi <= 100_000 ? (int) dpi : 0;
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }
}
