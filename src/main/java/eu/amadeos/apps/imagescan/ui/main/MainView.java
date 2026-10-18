package eu.amadeos.apps.imagescan.ui.main;

import eu.amadeos.apps.imagescan.domain.ImageFormat;
import eu.amadeos.apps.imagescan.domain.ScanSettings;
import eu.amadeos.apps.imagescan.domain.ScannedImage;
import eu.amadeos.apps.imagescan.domain.Scanner;
import eu.amadeos.apps.imagescan.domain.ScannerOptions;
import eu.amadeos.apps.imagescan.repository.ImagescanPreferences.Bar;

import java.awt.image.BufferedImage;
import java.awt.print.PrinterException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/** What {@link MainPresenter} needs from the window; tests replace it. */
public interface MainView {
    /** Which commands can run now; menus and toolbar follow. */
    record Commands(boolean scannerReady, boolean scanning, boolean busy, boolean hasImage, boolean editable,
                    boolean hasSelection, boolean canUndo, boolean canRedo) {
        public boolean canScan() {
            return scannerReady && !scanning && !busy;
        }

        public boolean canEdit() {
            return editable && !scanning && !busy;
        }

        public boolean canCrop() {
            return canEdit() && hasSelection;
        }

        public boolean canSave() {
            return editable && !busy;
        }
    }

    /** Where and in which format to save. */
    record SaveTarget(Path path, ImageFormat format) {
    }

    enum SaveChoice {SAVE, DISCARD, CANCEL}

    /** Window title, e.g. "*Untitled scan - Image Scan". */
    void showTitle(String title);

    /** Fills the scanner list; {@code selected} is shown as chosen. */
    void showScanners(List<Scanner> scanners, Optional<Scanner> selected);

    /** Fills mode and resolution with what the scanner offers and shows the current choice. */
    void showScanSettings(ScannerOptions options, ScanSettings settings);

    /** What will be scanned: "Whole glass (216 × 297 mm)", and the size of the result. */
    void showScanArea(String area, String estimate);

    /** Shows an image; the frame is removed. */
    void showImage(ScannedImage image, ImageKind kind);

    /** Back to the empty desk. */
    void clearImage();

    void clearSelection();

    void showStatus(String text);

    /** Shows the progress bar: a fraction from 0 to 1, or empty while the progress is not known. */
    void showProgress(OptionalDouble fraction);

    void hideProgress();

    void updateCommands(Commands commands);

    /** Asks for an image to open; empty if cancelled. */
    Optional<Path> chooseOpenPath();

    /**
     * Asks where to save: the name is suggested in the folder last used, the format preselected; a name
     * without extension gets the format's, replacing an existing file is confirmed. Empty if cancelled.
     */
    Optional<SaveTarget> chooseSavePath(String suggestedName, ImageFormat format);

    /** "Save the scan before …?" */
    SaveChoice confirmSave(String name);

    /** A yes/no question; true for yes. */
    boolean confirm(String message, String title);

    /**
     * Shows the print dialog and prints.
     *
     * @return false if the user cancelled
     */
    boolean print(ScannedImage image) throws PrinterException;

    void copyToClipboard(BufferedImage image);

    /** Shows or hides the toolbar, the scan settings or the status bar. */
    void showBar(Bar bar, boolean visible);

    void showError(String message);

    void showInfo(String message);

    void showAbout();

    void dispose();
}
