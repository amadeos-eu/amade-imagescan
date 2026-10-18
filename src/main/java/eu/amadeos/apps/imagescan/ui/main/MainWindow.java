package eu.amadeos.apps.imagescan.ui.main;

import com.google.inject.Inject;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.apps.imagescan.bootstrap.AmadeImagescanApplication;
import eu.amadeos.apps.imagescan.domain.ImageFormat;
import eu.amadeos.apps.imagescan.domain.ScanSettings;
import eu.amadeos.apps.imagescan.domain.ScannedImage;
import eu.amadeos.apps.imagescan.domain.Scanner;
import eu.amadeos.apps.imagescan.domain.ScannerOptions;
import eu.amadeos.apps.imagescan.repository.ImagescanPreferences.Bar;
import eu.amadeos.apps.imagescan.service.print.PrintService;
import eu.amadeos.apps.imagescan.ui.view.ImageView;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.border.BevelBorder;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;

import javax.swing.Action;

/**
 * The main window: menu bar and toolbar, the scan settings on the left, the image on its desk, and a status
 * bar with progress, cancel and zoom.
 */
public final class MainWindow extends JFrame implements MainView {
    private final Messages msg;
    private final LocaleSettings locales;
    private final PrintService printService;
    private final ImageView imageView = new ImageView();
    private final JLabel status = new JLabel(" ");
    private final JLabel zoom = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar(0, 1000);
    private final JPanel statusBar;
    private ScanSettingsPanel settingsPanel;
    private MainToolBar toolBar;
    private ScanActions actions;
    private MainPresenter presenter;
    private MainView.Commands commands = new MainView.Commands(false, false, false, false, false, false, false, false);
    private File lastDirectory;

    @Inject
    public MainWindow(Messages msg, LocaleSettings locales, PrintService printService) {
        super(msg.get("window.title"));
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.locales = locales;
        this.printService = printService;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        imageView.setName("image");
        imageView.setPlaceholder(msg.get("placeholder.title"), msg.get("placeholder.hint"));
        JPanel desk = new JPanel(new BorderLayout());
        desk.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        desk.add(imageView, BorderLayout.CENTER);
        add(desk, BorderLayout.CENTER);
        statusBar = statusBar();
        add(statusBar, BorderLayout.SOUTH);
        java.net.URL icon = getClass().getResource("/icons/amade-imagescan.png");
        if (icon != null) setIconImage(new javax.swing.ImageIcon(icon).getImage());
        setPreferredSize(new Dimension(1040, 760));
        pack();
        setLocationRelativeTo(null);
    }

    public void bind(MainPresenter presenter) {
        EdtExecutor.requireEdt();
        this.presenter = presenter;
        actions = new ScanActions(presenter, imageView, msg);
        setJMenuBar(new MainMenuBar(presenter, actions, msg, locales));
        toolBar = new MainToolBar(actions);
        add(toolBar, BorderLayout.NORTH);
        settingsPanel = new ScanSettingsPanel(presenter, actions, msg);
        add(settingsPanel, BorderLayout.WEST);
        imageView.setSizeLabel(presenter::frameLabel);
        imageView.setListener(new ImageView.Listener() {
            @Override
            public void selectionChanged(Optional<java.awt.Rectangle> selection) {
                presenter.selectionChanged(selection);
            }

            @Override
            public void zoomChanged(int percent) {
                zoom.setText(imageView.image() == null ? " " : msg.format("status.zoom", percent));
            }
        });
        // Accelerators also when the focus is not in a menu, e.g. Escape on the image.
        for (Action a : actions.all()) {
            KeyStroke key = (KeyStroke) a.getValue(Action.ACCELERATOR_KEY);
            if (key == null) continue;
            String name = String.valueOf(a.getValue(Action.ACTION_COMMAND_KEY));
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
            getRootPane().getActionMap().put(name, a);
        }
        for (Bar bar : Bar.values()) showBar(bar, presenter.isBarVisible(bar));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                presenter.exit();
            }
        });
        updateCommands(commands);
        pack();
        setLocationRelativeTo(null);
    }

    public ImageView imageView() {
        return imageView;
    }

    public ScanActions actions() {
        return actions;
    }

    ScanSettingsPanel settingsPanel() {
        return settingsPanel;
    }

    String statusText() {
        return status.getText();
    }

    JProgressBar progressBar() {
        return progress;
    }

    // ---------------------------------------------------------------- MainView

    @Override
    public void showTitle(String title) {
        EdtExecutor.requireEdt();
        setTitle(title);
    }

    @Override
    public void showScanners(List<Scanner> scanners, Optional<Scanner> selected) {
        EdtExecutor.requireEdt();
        settingsPanel.showScanners(scanners, selected);
    }

    @Override
    public void showScanSettings(ScannerOptions options, ScanSettings settings) {
        EdtExecutor.requireEdt();
        settingsPanel.showScanSettings(options, settings);
    }

    @Override
    public void showScanArea(String area, String estimate) {
        EdtExecutor.requireEdt();
        settingsPanel.showArea(area, estimate);
    }

    @Override
    public void showImage(ScannedImage image, ImageKind kind) {
        EdtExecutor.requireEdt();
        imageView.setImage(image.image(), kind == ImageKind.PREVIEW ? msg.get("badge.preview") : null);
    }

    @Override
    public void clearImage() {
        EdtExecutor.requireEdt();
        imageView.setImage(null, null);
    }

    @Override
    public void clearSelection() {
        EdtExecutor.requireEdt();
        imageView.clearSelection();
    }

    @Override
    public void showStatus(String text) {
        EdtExecutor.requireEdt();
        status.setText(text);
    }

    @Override
    public void showProgress(OptionalDouble fraction) {
        EdtExecutor.requireEdt();
        progress.setVisible(true);
        progress.setIndeterminate(fraction.isEmpty());
        if (fraction.isPresent()) {
            progress.setValue((int) Math.round(fraction.getAsDouble() * 1000));
            progress.setString(msg.format("status.percent", Math.round(fraction.getAsDouble() * 100)));
        } else {
            progress.setString("");
        }
    }

    @Override
    public void hideProgress() {
        EdtExecutor.requireEdt();
        progress.setIndeterminate(false);
        progress.setVisible(false);
    }

    @Override
    public void updateCommands(MainView.Commands newCommands) {
        EdtExecutor.requireEdt();
        commands = newCommands;
        if (actions != null) actions.update(newCommands);
        if (settingsPanel != null) settingsPanel.setScanning(newCommands.scanning(), newCommands.scannerReady());
    }

    @Override
    public Optional<Path> chooseOpenPath() {
        EdtExecutor.requireEdt();
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        String[] readable = ImageIO.getReaderFileSuffixes();
        FileNameExtensionFilter images = new FileNameExtensionFilter(msg.get("filter.images"), readable);
        chooser.addChoosableFileFilter(images);
        chooser.setFileFilter(images);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        lastDirectory = chooser.getCurrentDirectory();
        return Optional.of(chooser.getSelectedFile().toPath());
    }

    @Override
    public Optional<SaveTarget> chooseSavePath(String suggestedName, ImageFormat selected) {
        EdtExecutor.requireEdt();
        JFileChooser chooser = new JFileChooser(lastDirectory != null ? lastDirectory : picturesDirectory());
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        Map<FileFilter, ImageFormat> byFilter = new LinkedHashMap<>();
        for (ImageFormat format : ImageFormat.values()) {
            String patterns = String.join(", ", format.extensions().stream().map(e -> "*." + e).toList());
            FileNameExtensionFilter filter = new FileNameExtensionFilter(
                    msg.format("filter.format", msg.get("format." + format.defaultExtension()), patterns),
                    format.extensions().toArray(String[]::new));
            byFilter.put(filter, format);
            chooser.addChoosableFileFilter(filter);
            if (format == selected) chooser.setFileFilter(filter);
        }
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), suggestedName));
        // Switching the type changes the extension of the name.
        chooser.addPropertyChangeListener(JFileChooser.FILE_FILTER_CHANGED_PROPERTY, e -> {
            ImageFormat format = byFilter.get(chooser.getFileFilter());
            File current = chooser.getSelectedFile();
            if (format != null && current != null && !current.getName().isBlank()) {
                chooser.setSelectedFile(format.withExtension(new File(chooser.getCurrentDirectory(),
                        current.getName()).toPath()).toFile());
            }
        });
        while (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            ImageFormat format = byFilter.getOrDefault(chooser.getFileFilter(), selected);
            Path path = chooser.getSelectedFile().toPath();
            // The type chooser decides; a name typed with another image extension wins over it.
            Optional<ImageFormat> typed = ImageFormat.forPath(path);
            if (typed.isPresent()) format = typed.get();
            else path = format.withExtension(path);
            lastDirectory = chooser.getCurrentDirectory();
            if (!Files.exists(path) || confirm(msg.format("dialog.overwrite.message", path.getFileName()),
                    msg.get("dialog.overwrite.title"))) {
                return Optional.of(new SaveTarget(path, format));
            }
        }
        return Optional.empty();
    }

    /** ~/Pictures (or its translated name from the XDG user directories), else the home folder. */
    private static File picturesDirectory() {
        File home = new File(System.getProperty("user.home"));
        try {
            Path dirs = home.toPath().resolve(".config/user-dirs.dirs");
            if (Files.isRegularFile(dirs)) {
                for (String line : Files.readAllLines(dirs)) {
                    if (line.startsWith("XDG_PICTURES_DIR=")) {
                        String value = line.substring(line.indexOf('=') + 1).replace("\"", "")
                                .replace("$HOME", home.getPath());
                        File dir = new File(value);
                        if (dir.isDirectory()) return dir;
                    }
                }
            }
        } catch (java.io.IOException | RuntimeException e) {
            // fall through
        }
        File pictures = new File(home, "Pictures");
        return pictures.isDirectory() ? pictures : home;
    }

    @Override
    public SaveChoice confirmSave(String name) {
        EdtExecutor.requireEdt();
        int choice = JOptionPane.showConfirmDialog(this, plain(msg.format("dialog.save-changes.message", name)),
                msg.get("dialog.save-changes.title"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        return switch (choice) {
            case JOptionPane.YES_OPTION -> SaveChoice.SAVE;
            case JOptionPane.NO_OPTION -> SaveChoice.DISCARD;
            default -> SaveChoice.CANCEL;
        };
    }

    @Override
    public boolean confirm(String message, String title) {
        EdtExecutor.requireEdt();
        return JOptionPane.showConfirmDialog(this, plain(message), title, JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    @Override
    public boolean print(ScannedImage image) throws PrinterException {
        EdtExecutor.requireEdt();
        return printService.print(image, msg.get("window.title"));
    }

    @Override
    public void copyToClipboard(BufferedImage image) {
        EdtExecutor.requireEdt();
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new ImageSelection(image), null);
        showStatus(msg.get("status.copied"));
    }

    /** An image on the clipboard. */
    private record ImageSelection(Image image) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return image;
        }
    }

    @Override
    public void showBar(Bar bar, boolean visible) {
        EdtExecutor.requireEdt();
        switch (bar) {
            case TOOLBAR -> toolBar.setVisible(visible);
            case SETTINGS -> settingsPanel.setVisible(visible);
            case STATUS_BAR -> statusBar.setVisible(visible);
        }
        revalidate();
    }

    @Override
    public void showError(String message) {
        EdtExecutor.requireEdt();
        JOptionPane.showMessageDialog(this, plain(message), msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showInfo(String message) {
        EdtExecutor.requireEdt();
        JOptionPane.showMessageDialog(this, plain(message), getTitle(), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void showAbout() {
        EdtExecutor.requireEdt();
        JOptionPane.showMessageDialog(this,
                "<html><center><h2>amade-imagescan " + AmadeImagescanApplication.version() + "</h2>"
                        + "<p>" + msg.get("dialog.about.text") + "</p><br>"
                        + "<small>&copy; 2026 datazuul.com &bull; EUPL-1.2</small></center></html>",
                msg.get("dialog.about.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    /** A text component, so that messages (with file names, scanner errors) are not read as Swing HTML. */
    private static JTextArea plain(String message) {
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setOpaque(false);
        text.setLineWrap(message.length() > 200);
        text.setWrapStyleWord(true);
        if (message.length() > 200) text.setColumns(50);
        return text;
    }

    private JPanel statusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        Font small = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        status.setFont(small);
        status.setName("status");
        zoom.setFont(small);
        zoom.setName("zoom");
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        left.add(status);
        progress.setName("progress");
        progress.setStringPainted(true);
        progress.setPreferredSize(new Dimension(180, 16));
        progress.setVisible(false);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        right.add(progress);
        right.add(zoom);
        bar.add(left, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /** For tests: formats a frame like the label on the image does. */
    Function<java.awt.Rectangle, String> frameLabel() {
        return presenter::frameLabel;
    }
}
