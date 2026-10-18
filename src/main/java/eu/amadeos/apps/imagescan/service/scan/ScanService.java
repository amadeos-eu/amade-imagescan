package eu.amadeos.apps.imagescan.service.scan;

import com.google.inject.Inject;
import eu.amadeos.apps.imagescan.domain.ScanSettings;
import eu.amadeos.apps.imagescan.domain.Scanner;
import eu.amadeos.apps.imagescan.domain.ScannerOptions;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.DoubleConsumer;

/**
 * Runs the backend on the background executor. The futures complete there, so callers continue on the event
 * dispatch thread themselves. The options of each scanner are asked once.
 */
public class ScanService {
    private final ScannerBackend backend;
    private final ExecutorService executor;
    private final Map<String, ScannerOptions> options = new ConcurrentHashMap<>();

    @Inject
    public ScanService(ScannerBackend backend, ExecutorService executor) {
        this.backend = backend;
        this.executor = executor;
    }

    /** A running scan. */
    public record ScanTask(CompletableFuture<BufferedImage> result, ScanMonitor monitor) {
        /** Stops the scanner; the result then fails with {@link java.util.concurrent.CancellationException}. */
        public void cancel() {
            monitor.cancel();
        }
    }

    public ScannerBackend backend() {
        return backend;
    }

    public CompletableFuture<List<Scanner>> scanners() {
        return supply(backend::listScanners);
    }

    /** What the scanner can do; forgets earlier answers, so a scanner that was busy is asked again. */
    public CompletableFuture<ScannerOptions> options(Scanner scanner) {
        Objects.requireNonNull(scanner, "scanner");
        ScannerOptions known = options.get(scanner.id());
        if (known != null) return CompletableFuture.completedFuture(known);
        return supply(() -> {
            ScannerOptions found = backend.options(scanner);
            options.put(scanner.id(), found);
            return found;
        });
    }

    /** Forgets the options, after the scanner list was refreshed. */
    public void forgetOptions() {
        options.clear();
    }

    /**
     * Starts a scan.
     *
     * @param progress fraction done (or {@link ScanMonitor#UNKNOWN}); called on a background thread
     */
    public ScanTask scan(Scanner scanner, ScanSettings settings, ScannerOptions scannerOptions,
                         DoubleConsumer progress) {
        Objects.requireNonNull(scanner, "scanner");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(scannerOptions, "scannerOptions");
        ScanMonitor monitor = new ScanMonitor(progress);
        CompletableFuture<BufferedImage> result = supply(() -> backend.scan(scanner, settings, scannerOptions, monitor));
        return new ScanTask(result, monitor);
    }

    /** The cause of a failed future, unwrapped, for messages. */
    public static Throwable cause(Throwable failure) {
        Throwable t = failure;
        while (t instanceof CompletionException && t.getCause() != null) t = t.getCause();
        return t;
    }

    private interface Work<T> {
        T run() throws IOException, InterruptedException;
    }

    private <T> CompletableFuture<T> supply(Work<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.run();
            } catch (IOException e) {
                throw new CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        }, executor);
    }
}
