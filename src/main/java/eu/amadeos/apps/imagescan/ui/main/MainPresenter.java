package eu.amadeos.apps.imagescan.ui.main;

import com.google.inject.Inject;
import eu.amadeos.commons.app.Ui;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.process.ProgramMissingException;
import eu.amadeos.apps.imagescan.domain.ColorMode;
import eu.amadeos.apps.imagescan.domain.ImageFormat;
import eu.amadeos.apps.imagescan.domain.ScanArea;
import eu.amadeos.apps.imagescan.domain.ScanSettings;
import eu.amadeos.apps.imagescan.domain.ScannedImage;
import eu.amadeos.apps.imagescan.domain.Scanner;
import eu.amadeos.apps.imagescan.domain.ScannerOptions;
import eu.amadeos.apps.imagescan.repository.ImagescanPreferences;
import eu.amadeos.apps.imagescan.repository.ImagescanPreferences.Bar;
import eu.amadeos.apps.imagescan.service.image.ImageHistory;
import eu.amadeos.apps.imagescan.service.image.ImageOperations;
import eu.amadeos.apps.imagescan.service.image.ImageService;
import eu.amadeos.apps.imagescan.service.scan.ScanMonitor;
import eu.amadeos.apps.imagescan.service.scan.ScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The actions of the main window, without Swing: find scanners, preview, scan, cancel, edit (crop, turn,
 * mirror, undo), open, save, print, bars, language.
 *
 * <p>The workflow is that of XSane and Windows' scanning dialog: a quick preview of the whole glass, a frame
 * drawn around the part wanted, then the scan of just that part at the chosen resolution. A frame on a scan
 * is a crop instead.
 *
 * <p>All methods run on the event dispatch thread. Scanning, file access and edits of large images run in
 * the background and continue there through the {@link Ui} executor.
 */
public final class MainPresenter {
    private static final Logger LOG = LoggerFactory.getLogger(MainPresenter.class);
    /** Scans larger than this in memory are confirmed first. */
    static final long LARGE_SCAN_BYTES = 512L * 1024 * 1024;
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final MainView view;
    private final Messages msg;
    private final LocaleSettings locales;
    private final ImagescanPreferences preferences;
    private final ScanService scans;
    private final ImageService images;
    private final ExecutorService background;
    private final Executor ui;
    private final Supplier<LocalDateTime> clock;

    private List<Scanner> scanners = List.of();
    private Scanner scanner;
    private ScannerOptions options = ScannerOptions.FALLBACK;
    private boolean optionsReady;
    private ScanSettings settings;

    private final ImageHistory history = new ImageHistory();
    private ImageKind kind;
    private Optional<Rectangle> selection = Optional.empty();
    private Path file;
    private ImageFormat fileFormat;
    private boolean modified;

    private ScanService.ScanTask running;
    private boolean busy;
    private boolean closing;

    @Inject
    public MainPresenter(MainView view, Messages msg, LocaleSettings locales, ImagescanPreferences preferences,
                         ScanService scans, ImageService images, ExecutorService background, @Ui Executor ui) {
        this(view, msg, locales, preferences, scans, images, background, ui, LocalDateTime::now);
    }

    MainPresenter(MainView view, Messages msg, LocaleSettings locales, ImagescanPreferences preferences,
                  ScanService scans, ImageService images, ExecutorService background, Executor ui,
                  Supplier<LocalDateTime> clock) {
        this.view = view;
        this.msg = msg;
        this.locales = locales;
        this.preferences = preferences;
        this.scans = scans;
        this.images = images;
        this.background = background;
        this.ui = ui;
        this.clock = clock;
        this.settings = new ScanSettings(preferences.resolution(), preferences.mode(), Optional.empty());
        this.fileFormat = preferences.format();
    }

    /** After the window is shown: looks for scanners, and opens the image named on the command line. */
    public void start(List<String> arguments) {
        updateTitle();
        view.showStatus(msg.get("status.ready"));
        updateArea();
        updateCommands();
        refreshScanners();
        arguments.stream().filter(a -> !a.startsWith("--")).findFirst().map(Path::of).ifPresent(this::openNow);
    }

    // ---------------------------------------------------------------- state

    public Optional<ScannedImage> image() {
        return history.current();
    }

    public Optional<ImageKind> kind() {
        return Optional.ofNullable(kind);
    }

    public boolean isModified() {
        return modified;
    }

    public boolean isScanning() {
        return running != null;
    }

    public Optional<Scanner> scanner() {
        return Optional.ofNullable(scanner);
    }

    public ScanSettings settings() {
        return settings;
    }

    public Optional<Path> file() {
        return Optional.ofNullable(file);
    }

    private boolean editable() {
        return kind == ImageKind.SCAN || kind == ImageKind.FILE;
    }

    private String imageName() {
        return file == null ? msg.get("document.untitled") : String.valueOf(file.getFileName());
    }

    private void updateTitle() {
        if (kind == null || kind == ImageKind.PREVIEW) {
            view.showTitle(msg.get("window.title"));
        } else {
            view.showTitle(msg.format("window.title.document", (modified ? "*" : "") + imageName()));
        }
    }

    private void updateCommands() {
        view.updateCommands(new MainView.Commands(scanner != null && optionsReady, running != null, busy,
                kind != null, editable(), selection.isPresent(), history.canUndo() && editable(),
                history.canRedo() && editable()));
    }

    /** Shows a new image with nothing to undo. */
    private void showNew(ScannedImage image, ImageKind newKind, Path path, boolean isModified) {
        history.reset(image);
        kind = newKind;
        file = path;
        modified = isModified;
        selection = Optional.empty();
        view.showImage(image, newKind);
        updateTitle();
        updateArea();
        updateCommands();
    }

    /**
     * Runs the action if no scan would be lost, or after asking: saved first, discarded, or not at all if the
     * user cancels (or saving fails).
     */
    private void whenSafe(Runnable action) {
        if (busy) return;
        if (!modified || !editable()) {
            action.run();
            return;
        }
        switch (view.confirmSave(imageName())) {
            case SAVE -> save(action);
            case DISCARD -> action.run();
            case CANCEL -> {
            }
        }
    }

    // ---------------------------------------------------------------- scanners

    /** Asks the backend for the connected scanners and selects the one used last, else the first. */
    public void refreshScanners() {
        if (running != null) return;
        scans.forgetOptions();
        optionsReady = false;
        view.showStatus(msg.get("status.searching"));
        view.showProgress(OptionalDouble.empty());
        updateCommands();
        scans.scanners().whenCompleteAsync((found, failure) -> {
            view.hideProgress();
            if (failure != null) {
                Throwable cause = ScanService.cause(failure);
                LOG.warn("Cannot list scanners", cause);
                scanners = List.of();
                scanner = null;
                view.showScanners(scanners, Optional.empty());
                if (cause instanceof ProgramMissingException missing) {
                    view.showStatus(msg.get("status.no-backend"));
                    view.showError(msg.format("error.backend-missing", missing.program()));
                } else {
                    view.showStatus(msg.get("status.no-scanner"));
                    view.showError(msg.format("error.list", message(cause)));
                }
                updateCommands();
                return;
            }
            List<Scanner> sorted = new ArrayList<>(found);
            // Webcams that SANE also offers go last; they are rarely what is meant.
            sorted.sort(Comparator.comparing(Scanner::isCamera));
            scanners = List.copyOf(sorted);
            Optional<Scanner> chosen = preferences.scannerId()
                    .flatMap(id -> scanners.stream().filter(s -> s.id().equals(id)).findFirst())
                    .or(() -> scanners.stream().findFirst());
            view.showScanners(scanners, chosen);
            if (chosen.isEmpty()) {
                scanner = null;
                view.showStatus(msg.get("status.no-scanner"));
                updateCommands();
                return;
            }
            selectScanner(chosen.get());
        }, ui);
    }

    /** Another scanner chosen: reads what it can do. */
    public void selectScanner(Scanner chosen) {
        if (running != null || chosen == null) return;
        scanner = chosen;
        optionsReady = false;
        remember(() -> preferences.setScannerId(chosen.id()));
        view.showStatus(msg.format("status.reading-options", chosen.displayName()));
        updateCommands();
        scans.options(chosen).whenCompleteAsync((found, failure) -> {
            if (scanner != chosen) return;
            if (failure != null) {
                LOG.warn("Cannot read the options of {}", chosen.id(), ScanService.cause(failure));
                options = ScannerOptions.FALLBACK;
                view.showStatus(msg.format("status.default-options", chosen.displayName()));
            } else {
                options = found;
                view.showStatus(msg.format("status.scanner-ready", chosen.displayName()));
            }
            optionsReady = true;
            ColorMode mode = options.modeName(settings.mode()).isPresent() ? settings.mode()
                    : options.colorModes().stream().findFirst().orElse(ColorMode.COLOR);
            settings = new ScanSettings(options.nearestResolution(preferences.resolution()), mode, Optional.empty());
            view.showScanSettings(options, settings);
            updateArea();
            updateCommands();
        }, ui);
    }

    public void setResolution(int dpi) {
        if (dpi == settings.resolution()) return;
        settings = settings.withResolution(dpi);
        remember(() -> preferences.setResolution(dpi));
        updateArea();
    }

    public void setMode(ColorMode mode) {
        if (mode == settings.mode()) return;
        settings = settings.withMode(mode);
        remember(() -> preferences.setMode(mode));
        updateArea();
    }

    // ---------------------------------------------------------------- scanning

    // --8<-- [start:scan-area]
    /** The frame in the preview as part of the glass; empty for the whole glass. */
    Optional<ScanArea> scanArea() {
        if (kind != ImageKind.PREVIEW || selection.isEmpty()) return Optional.empty();
        ScannedImage preview = history.current().orElseThrow();
        Rectangle r = selection.get();
        ScanArea area = ScanArea.fromPixels(r.x, r.y, r.width, r.height, preview.resolution());
        return Optional.of(options.hasGlassSize() ? area.clampTo(options.maxWidth(), options.maxHeight()) : area);
    }
    // --8<-- [end:scan-area]

    private int effectiveResolution() {
        return options.nearestResolution(settings.resolution());
    }

    private void updateArea() {
        Optional<ScanArea> area = scanArea();
        double width = area.map(ScanArea::width).orElse(options.hasGlassSize() ? options.maxWidth() : 0);
        double height = area.map(ScanArea::height).orElse(options.hasGlassSize() ? options.maxHeight() : 0);
        String text;
        if (area.isPresent()) text = msg.format("area.frame", Math.round(width), Math.round(height));
        else if (width > 0) text = msg.format("area.glass.size", Math.round(width), Math.round(height));
        else text = msg.get("area.glass");
        String estimate = "";
        if (width > 0 && height > 0) {
            long pw = pixels(width);
            long ph = pixels(height);
            estimate = msg.format("area.estimate", pw, ph, Math.max(1, Math.round(bytes(pw, ph) / (1024.0 * 1024))));
        }
        view.showScanArea(text, estimate);
    }

    private long pixels(double millimetres) {
        return Math.round(millimetres * effectiveResolution() / ScanArea.MM_PER_INCH);
    }

    private long bytes(long width, long height) {
        return switch (settings.mode()) {
            case COLOR -> width * height * 3;
            case GRAY -> width * height;
            case LINEART -> (width + 7) / 8 * height;
        };
    }

    /** Scans the whole glass quickly, to draw the frame of the scan on it. */
    public void preview() {
        if (!canStartScan()) return;
        whenSafe(() -> {
            ColorMode mode = options.modeName(ColorMode.COLOR).isPresent() ? ColorMode.COLOR
                    : options.colorModes().stream().findFirst().orElse(ColorMode.COLOR);
            int dpi = options.nearestResolution(ScanSettings.PREVIEW_RESOLUTION);
            startScan(new ScanSettings(dpi, mode, Optional.empty()), true);
        });
    }

    /** Scans with the chosen settings: the framed part of the preview, or the whole glass. */
    public void scan() {
        if (!canStartScan()) return;
        ScanSettings wanted = settings.withArea(scanArea());
        double width = wanted.area().map(ScanArea::width).orElse(options.maxWidth());
        double height = wanted.area().map(ScanArea::height).orElse(options.maxHeight());
        long size = bytes(pixels(width), pixels(height));
        if (size > LARGE_SCAN_BYTES && !view.confirm(msg.format("dialog.large-scan.message",
                Math.round(size / (1024.0 * 1024)), effectiveResolution()), msg.get("dialog.large-scan.title"))) {
            return;
        }
        whenSafe(() -> startScan(wanted, false));
    }

    private boolean canStartScan() {
        return scanner != null && optionsReady && running == null && !busy;
    }

    // --8<-- [start:start-scan]
    private void startScan(ScanSettings scanSettings, boolean isPreview) {
        Scanner device = scanner;
        int dpi = options.nearestResolution(scanSettings.resolution());
        view.showStatus(msg.format(isPreview ? "status.previewing" : "status.scanning", device.displayName()));
        view.showProgress(OptionalDouble.empty());
        ScanService.ScanTask task = scans.scan(device, scanSettings, options, fraction -> ui.execute(() -> {
            if (running != null) {
                view.showProgress(fraction == ScanMonitor.UNKNOWN ? OptionalDouble.empty() : OptionalDouble.of(fraction));
            }
        }));
        running = task;
        updateCommands();
        task.result().whenCompleteAsync((image, failure) -> {
            running = null;
            view.hideProgress();
            if (closing) return;
            if (failure != null) {
                Throwable cause = ScanService.cause(failure);
                if (cause instanceof CancellationException || task.monitor().isCancelled()) {
                    view.showStatus(msg.get("status.cancelled"));
                } else {
                    LOG.warn("Scan failed", cause);
                    view.showStatus(msg.get("status.failed"));
                    view.showError(msg.format("error.scan", device.displayName(), message(cause)));
                }
                updateCommands();
                return;
            }
            ScannedImage scanned = new ScannedImage(image, dpi);
            if (isPreview) {
                showNew(scanned, ImageKind.PREVIEW, null, false);
                view.showStatus(msg.get("status.preview-done"));
            } else {
                showNew(scanned, ImageKind.SCAN, null, true);
                view.showStatus(describe("status.scanned", scanned));
            }
        }, ui);
    }
    // --8<-- [end:start-scan]

    /** Stops the running scan; the image shown before stays. */
    public void cancelScan() {
        if (running == null) return;
        view.showStatus(msg.get("status.cancelling"));
        running.cancel();
    }

    private String describe(String key, ScannedImage image) {
        if (image.hasResolution()) {
            return msg.format(key + ".size", image.width(), image.height(), image.resolution(),
                    Math.round(image.widthMm()), Math.round(image.heightMm()));
        }
        return msg.format(key, image.width(), image.height());
    }

    // ---------------------------------------------------------------- frame

    /** The frame on the image changed. */
    public void selectionChanged(Optional<Rectangle> newSelection) {
        selection = newSelection;
        updateArea();
        updateCommands();
    }

    /**
     * Text of the label at the frame: on a preview the part of the glass and the size it will be scanned at,
     * on a scan its pixels and (with a known resolution) millimetres.
     */
    public String frameLabel(Rectangle r) {
        Optional<ScannedImage> current = history.current();
        if (current.isEmpty()) return "";
        ScannedImage image = current.get();
        if (kind == ImageKind.PREVIEW && image.hasResolution()) {
            ScanArea area = ScanArea.fromPixels(r.x, r.y, r.width, r.height, image.resolution());
            return msg.format("frame.preview", Math.round(area.width()), Math.round(area.height()),
                    pixels(area.width()), pixels(area.height()));
        }
        if (image.hasResolution()) {
            double mm = ScanArea.MM_PER_INCH / image.resolution();
            return msg.format("frame.scan.size", r.width, r.height, Math.round(r.width * mm), Math.round(r.height * mm));
        }
        return msg.format("frame.scan", r.width, r.height);
    }

    // ---------------------------------------------------------------- editing

    public void crop() {
        Optional<Rectangle> area = selection;
        if (area.isEmpty()) return;
        edit(image -> ImageOperations.crop(image, area.get()), "status.cropped");
    }

    public void rotateLeft() {
        edit(ImageOperations::rotateCounterClockwise, "status.rotated");
    }

    public void rotateRight() {
        edit(ImageOperations::rotateClockwise, "status.rotated");
    }

    public void rotate180() {
        edit(ImageOperations::rotate180, "status.rotated");
    }

    public void flipHorizontal() {
        edit(ImageOperations::flipHorizontal, "status.flipped");
    }

    public void flipVertical() {
        edit(ImageOperations::flipVertical, "status.flipped");
    }

    // --8<-- [start:edit]
    /** Runs an edit in the background; the result becomes a new step of the history. */
    private void edit(UnaryOperator<BufferedImage> operation, String statusKey) {
        if (!editable() || busy || running != null) return;
        ScannedImage before = history.current().orElseThrow();
        busy = true;
        updateCommands();
        CompletableFuture.supplyAsync(() -> before.with(operation.apply(before.image())), background)
                .whenCompleteAsync((after, failure) -> {
                    busy = false;
                    if (failure != null) {
                        Throwable cause = ScanService.cause(failure);
                        LOG.warn("Edit failed", cause);
                        view.showError(msg.format("error.edit", message(cause)));
                    } else {
                        history.push(after);
                        modified = true;
                        selection = Optional.empty();
                        view.showImage(after, kind);
                        view.showStatus(describe(statusKey, after));
                        updateTitle();
                    }
                    updateCommands();
                }, ui);
    }
    // --8<-- [end:edit]

    public void undo() {
        if (!editable() || busy || running != null) return;
        history.undo().ifPresent(this::showStep);
    }

    public void redo() {
        if (!editable() || busy || running != null) return;
        history.redo().ifPresent(this::showStep);
    }

    private void showStep(ScannedImage image) {
        modified = true;
        selection = Optional.empty();
        view.showImage(image, kind);
        view.showStatus(describe("status.image", image));
        updateTitle();
        updateCommands();
    }

    public void copy() {
        history.current().filter(i -> kind != null).ifPresent(i -> view.copyToClipboard(i.image()));
    }

    // ---------------------------------------------------------------- files

    public void chooseOpen() {
        if (running != null) return;
        whenSafe(() -> view.chooseOpenPath().ifPresent(this::openNow));
    }

    /** Opens an image, asking first if a scan would be lost. */
    public void open(Path path) {
        if (running != null) return;
        whenSafe(() -> openNow(path));
    }

    private void openNow(Path path) {
        busy = true;
        updateCommands();
        images.open(path).whenCompleteAsync((opened, failure) -> {
            busy = false;
            if (failure != null) {
                Throwable cause = ScanService.cause(failure);
                LOG.warn("Cannot open {}", path, cause);
                view.showError(msg.format("error.open", path.getFileName(), message(cause)));
                updateCommands();
                return;
            }
            fileFormat = ImageFormat.forPath(path).orElse(fileFormat);
            showNew(opened, ImageKind.FILE, path, false);
            view.showStatus(describe("status.image", opened));
        }, ui);
    }

    public void save() {
        save(() -> {
        });
    }

    public void saveAs() {
        saveAs(() -> {
        });
    }

    private void save(Runnable then) {
        if (!editable()) return;
        if (file == null) saveAs(then);
        else saveTo(file, fileFormat, then);
    }

    private void saveAs(Runnable then) {
        if (!editable() || busy) return;
        String name = file != null ? String.valueOf(file.getFileName())
                : msg.get("file.prefix") + "_" + FILE_STAMP.format(clock.get()) + "." + fileFormat.defaultExtension();
        view.chooseSavePath(name, fileFormat).ifPresent(target -> saveTo(target.path(), target.format(), then));
    }

    private void saveTo(Path path, ImageFormat format, Runnable then) {
        ScannedImage image = history.current().orElseThrow();
        busy = true;
        updateCommands();
        view.showStatus(msg.format("status.saving", path.getFileName()));
        images.save(image, path, format, preferences.jpegQuality()).whenCompleteAsync((ignored, failure) -> {
            busy = false;
            if (failure != null) {
                Throwable cause = ScanService.cause(failure);
                LOG.warn("Cannot save {}", path, cause);
                view.showStatus(msg.get("status.ready"));
                view.showError(msg.format("error.save", path.getFileName(), message(cause)));
                updateCommands();
                return;
            }
            file = path;
            fileFormat = format;
            remember(() -> preferences.setFormat(format));
            // A later edit of the same image is a change again; this version is on disk.
            if (history.current().orElse(null) == image) modified = false;
            kind = ImageKind.FILE;
            view.showStatus(msg.format("status.saved", path.getFileName()));
            updateTitle();
            updateCommands();
            then.run();
        }, ui);
    }

    public void print() {
        Optional<ScannedImage> image = history.current();
        if (image.isEmpty() || kind == null) return;
        try {
            view.print(image.get());
        } catch (PrinterException | RuntimeException e) {
            LOG.warn("Printing failed", e);
            view.showError(msg.format("error.print", e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    private static String message(Throwable t) {
        if (t instanceof NoSuchFileException e) return e.getFile();
        if (t instanceof AccessDeniedException e) return e.getFile();
        if (t instanceof CompletionException && t.getCause() != null) return message(t.getCause());
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }

    // ---------------------------------------------------------------- other commands

    public boolean isBarVisible(Bar bar) {
        return preferences.isVisible(bar);
    }

    /** Shows or hides a bar and remembers it; the window changes even if the setting cannot be saved. */
    public void setBarVisible(Bar bar, boolean visible) {
        view.showBar(bar, visible);
        try {
            preferences.setVisible(bar, visible);
        } catch (UncheckedIOException e) {
            LOG.warn("Could not save the preferences", e);
            view.showError(msg.format("error.save-preferences", e.getCause().getMessage()));
        }
    }

    /** Stores a choice; a full disk is only logged, the choice still applies until the program ends. */
    private void remember(Runnable store) {
        try {
            store.run();
        } catch (UncheckedIOException e) {
            LOG.warn("Could not save the preferences", e);
        }
    }

    public void setLanguage(Optional<Language> language) {
        try {
            locales.setApplicationLanguage(language);
        } catch (IOException e) {
            LOG.warn("Could not save the language", e);
            view.showError(msg.format("error.save-language", e.getMessage()));
            return;
        }
        if (locales.restartRequired()) {
            view.showInfo(Messages.commons(msg.locale()).format("language.restart", "amade-imagescan"));
        }
    }

    public void about() {
        view.showAbout();
    }

    /** Closes the window, asking first if a scan would be lost; a running scan is stopped. */
    public void exit() {
        whenSafe(() -> {
            closing = true;
            if (running != null) running.cancel();
            view.dispose();
        });
    }
}
