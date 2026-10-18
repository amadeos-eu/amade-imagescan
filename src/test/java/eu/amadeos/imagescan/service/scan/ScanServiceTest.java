package eu.amadeos.imagescan.service.scan;

import eu.amadeos.imagescan.QueueExecutor;
import eu.amadeos.imagescan.domain.ScanSettings;
import eu.amadeos.imagescan.domain.Scanner;
import eu.amadeos.imagescan.domain.ScannerOptions;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanServiceTest {
    private static final Scanner S = new Scanner("test:0", "", "", "");

    private static final class CountingBackend implements ScannerBackend {
        final AtomicInteger optionCalls = new AtomicInteger();

        @Override
        public String name() {
            return "test";
        }

        @Override
        public List<Scanner> listScanners() {
            return List.of(S);
        }

        @Override
        public ScannerOptions options(Scanner scanner) throws IOException {
            if (optionCalls.incrementAndGet() == 1) throw new IOException("busy");
            return ScannerOptions.FALLBACK;
        }

        @Override
        public BufferedImage scan(Scanner scanner, ScanSettings settings, ScannerOptions options, ScanMonitor monitor) {
            monitor.progress(0.5);
            monitor.checkCancelled();
            return new BufferedImage(2, 2, BufferedImage.TYPE_BYTE_GRAY);
        }
    }

    @Test
    void optionsAreAskedAgainAfterAFailureAndThenKept() {
        QueueExecutor executor = new QueueExecutor();
        CountingBackend backend = new CountingBackend();
        ScanService service = new ScanService(backend, executor);
        CompletableFuture<ScannerOptions> first = service.options(S);
        executor.runAll();
        assertTrue(first.isCompletedExceptionally());
        CompletableFuture<ScannerOptions> second = service.options(S);
        executor.runAll();
        assertEquals(ScannerOptions.FALLBACK, second.join());
        assertTrue(service.options(S).isDone());
        assertEquals(2, backend.optionCalls.get());
        service.forgetOptions();
        service.options(S);
        executor.runAll();
        assertEquals(3, backend.optionCalls.get());
    }

    @Test
    void scanRunsInTheBackgroundAndCanBeCancelled() {
        QueueExecutor executor = new QueueExecutor();
        ScanService service = new ScanService(new CountingBackend(), executor);
        double[] progress = {-2};
        ScanService.ScanTask task = service.scan(S, ScanSettings.DEFAULT, ScannerOptions.FALLBACK, p -> progress[0] = p);
        assertFalse(task.result().isDone());
        executor.runAll();
        assertEquals(2, task.result().join().getWidth());
        assertEquals(0.5, progress[0], 0);

        ScanService.ScanTask cancelled = service.scan(S, ScanSettings.DEFAULT, ScannerOptions.FALLBACK, p -> {
        });
        cancelled.cancel();
        executor.runAll();
        Throwable failure = cancelled.result().handle((r, t) -> t).join();
        assertInstanceOf(CancellationException.class, ScanService.cause(failure));
    }

    @Test
    void monitorRunsCancelActionsOnceAndLateOnesAtOnce() {
        ScanMonitor monitor = ScanMonitor.silent();
        AtomicInteger runs = new AtomicInteger();
        monitor.onCancel(runs::incrementAndGet);
        monitor.cancel();
        monitor.cancel();
        assertEquals(1, runs.get());
        monitor.onCancel(runs::incrementAndGet);
        assertEquals(2, runs.get());
        assertTrue(monitor.isCancelled());
    }
}
