package eu.amadeos.imagescan.service.image;

import com.google.inject.Inject;
import eu.amadeos.imagescan.domain.ImageFormat;
import eu.amadeos.imagescan.domain.ScannedImage;
import eu.amadeos.imagescan.repository.ImageFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

/**
 * Opens and saves images on the background executor; the futures complete there. Encoding a large scan takes
 * a moment, so it runs in the background as well.
 */
public class ImageService {
    private final ImageFiles files;
    private final ExecutorService executor;

    @Inject
    public ImageService(ImageFiles files, ExecutorService executor) {
        this.files = files;
        this.executor = executor;
    }

    public CompletableFuture<ScannedImage> open(Path path) {
        Objects.requireNonNull(path, "path");
        return CompletableFuture.supplyAsync(() -> {
            try {
                return ImageCodec.decode(files.read(path));
            } catch (IOException e) {
                throw new CompletionException(e);
            } catch (RuntimeException e) {
                throw new CompletionException(new IOException("Cannot read " + path.getFileName() + ": " + e, e));
            }
        }, executor);
    }

    public CompletableFuture<Void> save(ScannedImage image, Path path, ImageFormat format, float jpegQuality) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(format, "format");
        return CompletableFuture.runAsync(() -> {
            try {
                files.write(path, ImageCodec.encode(image, format, jpegQuality));
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }
}
