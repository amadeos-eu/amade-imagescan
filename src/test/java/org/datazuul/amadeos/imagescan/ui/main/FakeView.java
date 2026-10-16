package org.datazuul.amadeos.imagescan.ui.main;

import org.datazuul.amadeos.imagescan.domain.ImageFormat;
import org.datazuul.amadeos.imagescan.domain.ScanSettings;
import org.datazuul.amadeos.imagescan.domain.ScannedImage;
import org.datazuul.amadeos.imagescan.domain.Scanner;
import org.datazuul.amadeos.imagescan.domain.ScannerOptions;
import org.datazuul.amadeos.imagescan.repository.ImagescanPreferences.Bar;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/** Records what the presenter shows and answers its questions from queues. */
final class FakeView implements MainView {
    String title;
    List<Scanner> scanners = List.of();
    Optional<Scanner> selectedScanner = Optional.empty();
    ScannerOptions options;
    ScanSettings settings;
    String area;
    String estimate;
    ScannedImage image;
    ImageKind kind;
    String status;
    OptionalDouble progress;
    boolean progressVisible;
    Commands commands;
    final List<String> errors = new ArrayList<>();
    final List<String> infos = new ArrayList<>();
    final Deque<Optional<Path>> openAnswers = new ArrayDeque<>();
    final Deque<Optional<SaveTarget>> saveAnswers = new ArrayDeque<>();
    final Deque<SaveChoice> saveChoices = new ArrayDeque<>();
    final Deque<Boolean> confirmations = new ArrayDeque<>();
    String suggestedName;
    ImageFormat suggestedFormat;
    final List<String> asked = new ArrayList<>();
    BufferedImage clipboard;
    ScannedImage printed;
    boolean disposed;
    final List<Bar> hiddenBars = new ArrayList<>();

    @Override
    public void showTitle(String title) {
        this.title = title;
    }

    @Override
    public void showScanners(List<Scanner> scanners, Optional<Scanner> selected) {
        this.scanners = scanners;
        this.selectedScanner = selected;
    }

    @Override
    public void showScanSettings(ScannerOptions options, ScanSettings settings) {
        this.options = options;
        this.settings = settings;
    }

    @Override
    public void showScanArea(String area, String estimate) {
        this.area = area;
        this.estimate = estimate;
    }

    @Override
    public void showImage(ScannedImage image, ImageKind kind) {
        this.image = image;
        this.kind = kind;
    }

    @Override
    public void clearImage() {
        image = null;
        kind = null;
    }

    @Override
    public void clearSelection() {
    }

    @Override
    public void showStatus(String text) {
        status = text;
    }

    @Override
    public void showProgress(OptionalDouble fraction) {
        progress = fraction;
        progressVisible = true;
    }

    @Override
    public void hideProgress() {
        progressVisible = false;
    }

    @Override
    public void updateCommands(Commands commands) {
        this.commands = commands;
    }

    @Override
    public Optional<Path> chooseOpenPath() {
        return openAnswers.isEmpty() ? Optional.empty() : openAnswers.pop();
    }

    @Override
    public Optional<SaveTarget> chooseSavePath(String suggestedName, ImageFormat format) {
        this.suggestedName = suggestedName;
        this.suggestedFormat = format;
        return saveAnswers.isEmpty() ? Optional.empty() : saveAnswers.pop();
    }

    @Override
    public SaveChoice confirmSave(String name) {
        asked.add(name);
        return saveChoices.isEmpty() ? SaveChoice.CANCEL : saveChoices.pop();
    }

    @Override
    public boolean confirm(String message, String title) {
        asked.add(message);
        return !confirmations.isEmpty() && confirmations.pop();
    }

    @Override
    public boolean print(ScannedImage image) {
        printed = image;
        return true;
    }

    @Override
    public void copyToClipboard(BufferedImage image) {
        clipboard = image;
    }

    @Override
    public void showBar(Bar bar, boolean visible) {
        if (!visible) hiddenBars.add(bar);
        else hiddenBars.remove(bar);
    }

    @Override
    public void showError(String message) {
        errors.add(message);
    }

    @Override
    public void showInfo(String message) {
        infos.add(message);
    }

    @Override
    public void showAbout() {
    }

    @Override
    public void dispose() {
        disposed = true;
    }
}
