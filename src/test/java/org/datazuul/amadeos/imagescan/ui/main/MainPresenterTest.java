package org.datazuul.amadeos.imagescan.ui.main;

import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.imagescan.QueueExecutor;
import org.datazuul.amadeos.imagescan.bootstrap.ImagescanModule;
import org.datazuul.amadeos.imagescan.domain.ColorMode;
import org.datazuul.amadeos.imagescan.domain.ImageFormat;
import org.datazuul.amadeos.imagescan.domain.ScanArea;
import org.datazuul.amadeos.imagescan.domain.ScanSettings;
import org.datazuul.amadeos.imagescan.repository.ImageFiles;
import org.datazuul.amadeos.imagescan.repository.ImagescanPreferences;
import org.datazuul.amadeos.imagescan.repository.ImagescanPreferences.Bar;
import org.datazuul.amadeos.imagescan.service.image.ImageCodec;
import org.datazuul.amadeos.imagescan.service.image.ImageService;
import org.datazuul.amadeos.imagescan.service.scan.BackendMissingException;
import org.datazuul.amadeos.imagescan.service.scan.ScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainPresenterTest {
    static final Messages MSG = Messages.load(ImagescanModule.class, ImagescanModule.MESSAGES, Locale.ENGLISH);

    @TempDir
    Path dir;

    private final QueueExecutor executor = new QueueExecutor();
    private final FakeView view = new FakeView();
    private final FakeBackend backend = new FakeBackend();
    private ImagescanPreferences preferences;
    private MainPresenter presenter;

    @BeforeEach
    void setUp() {
        preferences = new ImagescanPreferences(dir.resolve("imagescan.properties"));
        // Small images keep the tests fast: 75 dpi unless a test needs another.
        preferences.setResolution(75);
        presenter = new MainPresenter(view, MSG, null, preferences, new ScanService(backend, executor),
                new ImageService(new ImageFiles(), executor), executor, executor,
                () -> LocalDateTime.of(2026, 10, 16, 12, 45, 30));
    }

    private void startWithScanner() {
        presenter.start(List.of());
        executor.runAll();
    }

    @Test
    void findsScannersAndPrefersTheFlatbed() {
        startWithScanner();
        assertEquals(List.of(FakeBackend.EPSON, FakeBackend.CAMERA), view.scanners);
        assertEquals(Optional.of(FakeBackend.EPSON), view.selectedScanner);
        assertEquals(FakeBackend.OPTIONS, view.options);
        assertEquals(75, view.settings.resolution());
        assertTrue(view.commands.canScan());
        assertFalse(view.commands.hasImage());
        assertEquals("Epson Perfection V39 is ready.", view.status);
        assertEquals("Whole glass (216 × 297 mm)", view.area);
        assertEquals("≈ 638 × 877 pixels, 2 MB", view.estimate);
        assertEquals(Optional.of(FakeBackend.EPSON.id()), preferences.scannerId());
        assertEquals("Image Scan", view.title);
    }

    @Test
    void remembersTheScannerUsedLast() {
        preferences.setScannerId(FakeBackend.CAMERA.id());
        startWithScanner();
        assertEquals(Optional.of(FakeBackend.CAMERA), view.selectedScanner);
    }

    @Test
    void explainsAMissingScanimage() {
        backend.listFailure = new BackendMissingException("scanimage", new IOException("Cannot run program"));
        startWithScanner();
        assertEquals("Scanning is not available.", view.status);
        assertTrue(view.errors.getFirst().contains("sudo apt install sane-utils"), view.errors.toString());
        assertFalse(view.commands.canScan());
    }

    @Test
    void saysWhenNoScannerIsConnected() {
        backend.scanners = List.of();
        startWithScanner();
        assertTrue(view.status.startsWith("No scanner found."));
        assertFalse(view.commands.canScan());
        assertTrue(view.errors.isEmpty());
    }

    @Test
    void previewThenScanOnlyTheFramedPart() {
        startWithScanner();
        presenter.setResolution(300);
        presenter.preview();
        assertTrue(view.commands.scanning());
        assertTrue(view.progressVisible);
        executor.runAll();
        assertEquals(ImageKind.PREVIEW, view.kind);
        assertEquals(75, view.image.resolution());
        assertEquals(ScanSettings.PREVIEW_RESOLUTION, backend.scans.getFirst().resolution());
        assertEquals(Optional.empty(), backend.scans.getFirst().area());
        assertFalse(view.commands.canEdit());
        assertFalse(view.progressVisible);

        // A frame of 1 × 2 inches, 1 inch from the left and top, in the 75 dpi preview.
        presenter.selectionChanged(Optional.of(new Rectangle(75, 75, 75, 150)));
        assertEquals("Frame in the preview (25 × 51 mm)", view.area);
        assertEquals("≈ 300 × 600 pixels, 1 MB", view.estimate);
        assertEquals("25 × 51 mm → 300 × 600 px", presenter.frameLabel(new Rectangle(75, 75, 75, 150)));
        assertFalse(view.commands.canCrop(), "a frame on a preview is the scan area, not a crop");

        presenter.scan();
        executor.runAll();
        ScanSettings scanned = backend.scans.get(1);
        assertEquals(300, scanned.resolution());
        ScanArea area = scanned.area().orElseThrow();
        assertEquals(25.4, area.left(), 1e-9);
        assertEquals(50.8, area.height(), 1e-9);
        assertEquals(ImageKind.SCAN, view.kind);
        assertEquals(300, view.image.width());
        assertEquals(600, view.image.height());
        assertEquals(300, view.image.resolution());
        assertEquals("*Untitled scan - Image Scan", view.title);
        assertEquals("Scanned: 300 × 600 px at 300 dpi (25 × 51 mm).", view.status);
        assertTrue(view.commands.canEdit());
        // The scan area goes back to the whole glass.
        assertEquals("Whole glass (216 × 297 mm)", view.area);
    }

    @Test
    void aFrameOnAScanCropsIt() {
        startWithScanner();
        presenter.scan();
        executor.runAll();
        assertEquals(Optional.empty(), backend.scans.getFirst().area());
        presenter.selectionChanged(Optional.of(new Rectangle(10, 20, 100, 50)));
        assertTrue(view.commands.canCrop());
        assertEquals("100 × 50 px · 34 × 17 mm", presenter.frameLabel(new Rectangle(10, 20, 100, 50)));
        presenter.crop();
        assertTrue(view.commands.busy());
        executor.runAll();
        assertEquals(100, view.image.width());
        assertEquals(50, view.image.height());
        assertEquals(75, view.image.resolution());
        assertTrue(view.commands.canUndo());
        assertFalse(view.commands.hasSelection());

        presenter.rotateRight();
        executor.runAll();
        assertEquals(50, view.image.width());
        presenter.undo();
        assertEquals(100, view.image.width());
        presenter.undo();
        assertEquals(638, view.image.width());
        assertFalse(view.commands.canUndo());
        presenter.redo();
        assertEquals(100, view.image.width());
    }

    @Test
    void cancelKeepsThePreviousImage() {
        startWithScanner();
        presenter.preview();
        executor.runAll();
        var preview = view.image;
        presenter.scan();
        presenter.cancelScan();
        assertEquals("Stopping the scanner...", view.status);
        executor.runAll();
        assertEquals("Scan cancelled.", view.status);
        assertSame(preview, view.image);
        assertEquals(ImageKind.PREVIEW, view.kind);
        assertTrue(view.errors.isEmpty());
        assertTrue(view.commands.canScan());
    }

    @Test
    void scanErrorsAreShown() {
        startWithScanner();
        backend.scanFailure = new IOException("scanimage: open of device epson2 failed: Device busy");
        presenter.scan();
        executor.runAll();
        assertEquals("Scan failed.", view.status);
        assertEquals("Scanning on Epson Perfection V39 failed:\nscanimage: open of device epson2 failed: Device busy",
                view.errors.getFirst());
        assertNull(view.image);
    }

    @Test
    void largeScansAreConfirmed() {
        backend.options = new org.datazuul.amadeos.imagescan.domain.ScannerOptions(List.of(75, 2400),
                FakeBackend.OPTIONS.modes(), 215.9, 297.0);
        startWithScanner();
        presenter.setResolution(2400);
        // A4 at 2400 dpi in colour: about 1.6 GB.
        view.confirmations.push(false);
        presenter.scan();
        assertEquals(1, view.asked.size());
        assertTrue(view.asked.getFirst().contains("2400 dpi"), view.asked.toString());
        executor.runAll();
        assertTrue(backend.scans.isEmpty());
        // A small frame in black and white needs no question.
        presenter.setMode(ColorMode.LINEART);
        presenter.preview();
        executor.runAll();
        presenter.selectionChanged(Optional.of(new Rectangle(0, 0, 100, 100)));
        presenter.scan();
        assertEquals(1, view.asked.size());
    }

    @Test
    void anUnsavedScanIsNotLostWithoutAsking() {
        startWithScanner();
        presenter.scan();
        executor.runAll();
        view.saveChoices.push(MainView.SaveChoice.CANCEL);
        presenter.preview();
        executor.runAll();
        assertEquals(List.of("Untitled scan"), view.asked);
        assertEquals(1, backend.scans.size());

        view.saveChoices.push(MainView.SaveChoice.DISCARD);
        presenter.preview();
        executor.runAll();
        assertEquals(2, backend.scans.size());
        assertEquals(ImageKind.PREVIEW, view.kind);

        // A preview is never asked about.
        presenter.preview();
        executor.runAll();
        assertEquals(2, view.asked.size());
        assertEquals(3, backend.scans.size());
    }

    @Test
    void savesWithResolutionAndSuggestsADatedName() throws IOException {
        startWithScanner();
        presenter.scan();
        executor.runAll();
        Path target = dir.resolve("letter.tif");
        view.saveAnswers.push(Optional.of(new MainView.SaveTarget(target, ImageFormat.TIFF)));
        presenter.save();
        executor.runAll();
        assertEquals("scan_2026-10-16_12-45-30.png", view.suggestedName);
        assertTrue(Files.exists(target));
        assertEquals(75, ImageCodec.decode(Files.readAllBytes(target)).resolution());
        assertEquals("letter.tif - Image Scan", view.title);
        assertEquals("Saved letter.tif.", view.status);
        assertEquals(ImageFormat.TIFF, preferences.format());
        assertFalse(presenter.isModified());

        // Save again goes to the same file without asking.
        presenter.rotateLeft();
        executor.runAll();
        assertEquals("*letter.tif - Image Scan", view.title);
        presenter.save();
        executor.runAll();
        assertTrue(view.saveAnswers.isEmpty());
        assertEquals(877, ImageCodec.decode(Files.readAllBytes(target)).width());
    }

    @Test
    void saveBeforeContinuing() {
        startWithScanner();
        presenter.scan();
        executor.runAll();
        view.saveChoices.push(MainView.SaveChoice.SAVE);
        view.saveAnswers.push(Optional.of(new MainView.SaveTarget(dir.resolve("a.png"), ImageFormat.PNG)));
        presenter.exit();
        assertFalse(view.disposed);
        executor.runAll();
        assertTrue(Files.exists(dir.resolve("a.png")));
        assertTrue(view.disposed);
    }

    @Test
    void opensImagesFromTheCommandLine() throws IOException {
        Path file = dir.resolve("photo.jpg");
        Files.write(file, ImageCodec.encode(new org.datazuul.amadeos.imagescan.domain.ScannedImage(
                org.datazuul.amadeos.imagescan.TestImages.rgb(12, 8), 200), ImageFormat.JPEG, 0.9f));
        presenter.start(List.of("--verbose", file.toString()));
        executor.runAll();
        assertEquals(ImageKind.FILE, view.kind);
        assertEquals(12, view.image.width());
        assertEquals(200, view.image.resolution());
        assertEquals("photo.jpg - Image Scan", view.title);
        assertTrue(view.commands.canEdit());
        presenter.copy();
        assertNotNull(view.clipboard);
        presenter.print();
        assertSame(view.image, view.printed);
    }

    @Test
    void openFailureIsReported() {
        presenter.open(dir.resolve("missing.png"));
        executor.runAll();
        assertTrue(view.errors.getFirst().startsWith("Could not open missing.png"), view.errors.toString());
        assertFalse(view.commands.busy());
    }

    @Test
    void modeAndResolutionAreRemembered() {
        startWithScanner();
        presenter.setMode(ColorMode.LINEART);
        presenter.setResolution(600);
        assertEquals(ColorMode.LINEART, preferences.mode());
        assertEquals(600, preferences.resolution());
        assertEquals("≈ 5100 × 7016 pixels, 4 MB", view.estimate);
    }

    @Test
    void barsAreShownAndRemembered() {
        presenter.setBarVisible(Bar.SETTINGS, false);
        assertEquals(List.of(Bar.SETTINGS), view.hiddenBars);
        assertFalse(presenter.isBarVisible(Bar.SETTINGS));
    }
}
