package eu.amadeos.apps.imagescan.domain;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The file types scans are saved in. */
public enum ImageFormat {
    /** Lossless, for documents and line art; the default. */
    PNG("PNG", "png", List.of("png")),
    /** Small files for photos; no transparency, lossy. */
    JPEG("JPEG", "jpeg", List.of("jpg", "jpeg")),
    /** Lossless, what archives and OCR programs expect. */
    TIFF("TIFF", "tiff", List.of("tif", "tiff"));

    private final String label;
    private final String imageIoName;
    private final List<String> extensions;

    ImageFormat(String label, String imageIoName, List<String> extensions) {
        this.label = label;
        this.imageIoName = imageIoName;
        this.extensions = extensions;
    }

    public String label() {
        return label;
    }

    /** The format name {@link javax.imageio.ImageIO} knows it by. */
    public String imageIoName() {
        return imageIoName;
    }

    public List<String> extensions() {
        return extensions;
    }

    public String defaultExtension() {
        return extensions.getFirst();
    }

    /** The format a file name's extension stands for. */
    public static Optional<ImageFormat> forPath(Path path) {
        String name = String.valueOf(path.getFileName());
        int dot = name.lastIndexOf('.');
        if (dot < 0) return Optional.empty();
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(f -> f.extensions.contains(ext)).findFirst();
    }

    /** The path with this format's extension: added if there is none, replaced if it is another image type. */
    public Path withExtension(Path path) {
        String name = String.valueOf(path.getFileName());
        Optional<ImageFormat> current = forPath(path);
        if (current.isPresent() && current.get() == this) return path;
        if (current.isPresent()) name = name.substring(0, name.lastIndexOf('.'));
        return path.resolveSibling(name + "." + defaultExtension());
    }
}
