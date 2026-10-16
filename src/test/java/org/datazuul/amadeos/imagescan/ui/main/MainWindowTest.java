package org.datazuul.amadeos.imagescan.ui.main;

import org.datazuul.amadeos.imagescan.OnEdt;
import org.datazuul.amadeos.imagescan.QueueExecutor;
import org.datazuul.amadeos.imagescan.domain.ColorMode;
import org.datazuul.amadeos.imagescan.repository.ImageFiles;
import org.datazuul.amadeos.imagescan.repository.ImagescanPreferences;
import org.datazuul.amadeos.imagescan.repository.ImagescanPreferences.Bar;
import org.datazuul.amadeos.imagescan.service.image.ImageService;
import org.datazuul.amadeos.imagescan.service.print.PrintService;
import org.datazuul.amadeos.imagescan.service.scan.ScanService;
import org.datazuul.amadeos.imagescan.ui.icons.ToolIcon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Action;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The real window with the fake scanner: settings, actions, preview, frame, scan. Needs a display (Xvfb in CI). */
class MainWindowTest {
    @TempDir
    Path dir;

    @Test
    void scanWorkflowThroughTheWindow() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        OnEdt.run(() -> {
            QueueExecutor executor = new QueueExecutor();
            ImagescanPreferences preferences = new ImagescanPreferences(dir.resolve("imagescan.properties"));
            preferences.setResolution(75);
            MainWindow window = new MainWindow(MainPresenterTest.MSG, null, new PrintService());
            MainPresenter presenter = new MainPresenter(window, MainPresenterTest.MSG, null, preferences,
                    new ScanService(new FakeBackend(), executor), new ImageService(new ImageFiles(), executor),
                    executor, executor, LocalDateTime::now);
            window.bind(presenter);
            try {
                ScanActions actions = window.actions();
                assertFalse(actions.scan.isEnabled());
                presenter.start(List.of());
                executor.runAll();
                assertTrue(actions.scan.isEnabled());
                assertFalse(actions.crop.isEnabled());
                ScanSettingsPanel panel = window.settingsPanel();
                assertEquals(2, panel.scannerBox().getItemCount());
                assertEquals(FakeBackend.EPSON, panel.scannerBox().getSelectedItem());
                assertEquals(3, panel.modeBox().getItemCount());
                assertEquals(75, panel.resolutionBox().getSelectedItem());
                panel.modeBox().setSelectedItem(ColorMode.GRAY);
                assertEquals(ColorMode.GRAY, presenter.settings().mode());
                panel.resolutionBox().setSelectedItem(150);
                assertEquals(150, presenter.settings().resolution());

                actions.preview.actionPerformed(null);
                assertTrue(actions.cancel.isEnabled());
                assertTrue(window.progressBar().isVisible());
                executor.runAll();
                assertFalse(window.progressBar().isVisible());
                assertNotNull(window.imageView().image());
                assertFalse(actions.rotateLeft.isEnabled());

                window.imageView().setSelection(new Rectangle(0, 0, 75, 75));
                assertTrue(panel.areaText().startsWith("Frame in the preview"), panel.areaText());
                actions.scan.actionPerformed(null);
                executor.runAll();
                BufferedImage scanned = window.imageView().image();
                assertEquals(150, scanned.getWidth());
                assertTrue(actions.rotateLeft.isEnabled());
                assertTrue(window.getTitle().startsWith("*"));

                window.imageView().selectAll();
                assertTrue(actions.crop.isEnabled());
                window.imageView().clearSelection();
                assertFalse(actions.crop.isEnabled());

                presenter.setBarVisible(Bar.SETTINGS, false);
                assertFalse(panel.isVisible());

                window.imageView().zoomActualSize();
                assertEquals(100, window.imageView().viewport().zoomPercent());

                for (Action a : actions.all()) {
                    assertNotNull(a.getValue(Action.NAME));
                    assertFalse(String.valueOf(a.getValue(Action.NAME)).startsWith("!"), String.valueOf(a.getValue(Action.NAME)));
                }
            } finally {
                window.dispose();
            }
        });
    }

    @Test
    void everyGlyphPaints() {
        BufferedImage canvas = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        var g = canvas.createGraphics();
        try {
            for (ToolIcon.Glyph glyph : ToolIcon.Glyph.values()) {
                ToolIcon icon = new ToolIcon(glyph, 32);
                icon.paintIcon(null, g, 0, 0);
                assertEquals(32, icon.getIconWidth());
            }
        } finally {
            g.dispose();
        }
        // Something was drawn.
        boolean painted = false;
        for (int y = 0; y < 32 && !painted; y++) {
            for (int x = 0; x < 32 && !painted; x++) painted = (canvas.getRGB(x, y) >>> 24) != 0;
        }
        assertTrue(painted);
    }
}
