package eu.amadeos.apps.imagescan.service.scan;

import eu.amadeos.apps.imagescan.domain.ScanSettings;
import eu.amadeos.apps.imagescan.domain.Scanner;
import eu.amadeos.apps.imagescan.domain.ScannerOptions;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * Talks to the scanners of the platform: SANE on Linux, WIA on Windows. All methods block and run on a
 * background thread.
 */
public interface ScannerBackend {
    /** Short name for messages and the log, e.g. "SANE". */
    String name();

    /**
     * The scanners connected now. Network scanners may take a few seconds to answer.
     *
     * @throws BackendMissingException if the scanning program is not installed
     */
    List<Scanner> listScanners() throws IOException, InterruptedException;

    /**
     * What the scanner can do. Backends that cannot ask the scanner return {@link ScannerOptions#FALLBACK}.
     */
    ScannerOptions options(Scanner scanner) throws IOException, InterruptedException;

    /**
     * Scans one image.
     *
     * @param options what {@link #options} returned for this scanner, to name the mode as the scanner does
     * @param monitor receives the progress and can cancel; the scan then ends with
     *                {@link java.util.concurrent.CancellationException}
     */
    BufferedImage scan(Scanner scanner, ScanSettings settings, ScannerOptions options, ScanMonitor monitor)
            throws IOException, InterruptedException;
}
