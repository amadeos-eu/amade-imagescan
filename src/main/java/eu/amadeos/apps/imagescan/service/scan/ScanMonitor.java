package eu.amadeos.apps.imagescan.service.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.DoubleConsumer;

/**
 * Between a running scan and whoever started it: the backend reports progress, the user may cancel. Safe to
 * use from several threads.
 */
public final class ScanMonitor {
    /** Progress not known (yet): the scanner is warming up or the backend does not report it. */
    public static final double UNKNOWN = -1;

    private final DoubleConsumer progressListener;
    private final List<Runnable> cancelActions = new ArrayList<>();
    private boolean cancelled;

    public ScanMonitor(DoubleConsumer progressListener) {
        this.progressListener = progressListener;
    }

    /** A monitor nobody listens to. */
    public static ScanMonitor silent() {
        return new ScanMonitor(p -> {
        });
    }

    /** Fraction done, 0 to 1, or {@link #UNKNOWN}. */
    public void progress(double fraction) {
        progressListener.accept(fraction < 0 ? UNKNOWN : Math.min(1, fraction));
    }

    // --8<-- [start:cancel]
    /** Runs the action on {@link #cancel}, or at once if the scan was already cancelled. */
    public void onCancel(Runnable action) {
        boolean now;
        synchronized (this) {
            now = cancelled;
            if (!now) cancelActions.add(action);
        }
        if (now) action.run();
    }

    public void cancel() {
        List<Runnable> actions;
        synchronized (this) {
            if (cancelled) return;
            cancelled = true;
            actions = List.copyOf(cancelActions);
            cancelActions.clear();
        }
        actions.forEach(Runnable::run);
    }
    // --8<-- [end:cancel]

    public synchronized boolean isCancelled() {
        return cancelled;
    }

    /** Ends the scan with a {@link CancellationException} if it was cancelled. */
    public void checkCancelled() {
        if (isCancelled()) throw new CancellationException("Scan cancelled");
    }
}
