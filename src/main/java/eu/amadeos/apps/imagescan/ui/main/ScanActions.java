package eu.amadeos.apps.imagescan.ui.main;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolIcon;
import eu.amadeos.commons.swing.ToolIcon.Glyph;
import eu.amadeos.apps.imagescan.ui.icons.ImagescanGlyph;
import eu.amadeos.apps.imagescan.ui.view.ImageView;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * One action per command, shared by the menus and the toolbar, so that both are enabled and disabled
 * together. Texts come from the messages ({@code &} marks the mnemonic), icons from {@link ToolIcon}.
 */
public final class ScanActions {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;

    public final Action open;
    public final Action save;
    public final Action saveAs;
    public final Action print;
    public final Action exit;
    public final Action undo;
    public final Action redo;
    public final Action copy;
    public final Action selectAll;
    public final Action clearSelection;
    public final Action crop;
    public final Action rotateLeft;
    public final Action rotateRight;
    public final Action rotate180;
    public final Action flipHorizontal;
    public final Action flipVertical;
    public final Action preview;
    public final Action scan;
    public final Action cancel;
    public final Action refresh;
    public final Action zoomIn;
    public final Action zoomOut;
    public final Action zoomFit;
    public final Action zoomActual;

    private final Messages msg;

    public ScanActions(MainPresenter presenter, ImageView view, Messages msg) {
        this.msg = msg;
        open = action("menu.file.open", StandardGlyph.OPEN, KeyEvent.VK_O, CTRL, presenter::chooseOpen);
        save = action("menu.file.save", StandardGlyph.SAVE, KeyEvent.VK_S, CTRL, presenter::save);
        saveAs = action("menu.file.save-as", null, KeyEvent.VK_S, CTRL | SHIFT, presenter::saveAs);
        print = action("menu.file.print", StandardGlyph.PRINT, KeyEvent.VK_P, CTRL, presenter::print);
        exit = action("menu.file.exit", null, KeyEvent.VK_Q, CTRL, presenter::exit);
        undo = action("menu.edit.undo", StandardGlyph.UNDO, KeyEvent.VK_Z, CTRL, presenter::undo);
        redo = action("menu.edit.redo", StandardGlyph.REDO, KeyEvent.VK_Y, CTRL, presenter::redo);
        copy = action("menu.edit.copy", null, KeyEvent.VK_C, CTRL, presenter::copy);
        selectAll = action("menu.edit.select-all", null, KeyEvent.VK_A, CTRL, view::selectAll);
        clearSelection = action("menu.edit.select-none", null, KeyEvent.VK_ESCAPE, 0, view::clearSelection);
        // --8<-- [start:actions]
        crop = action("menu.image.crop", ImagescanGlyph.CROP, KeyEvent.VK_X, CTRL | SHIFT, presenter::crop);
        rotateLeft = action("menu.image.rotate-left", ImagescanGlyph.ROTATE_LEFT, KeyEvent.VK_L, CTRL,
                presenter::rotateLeft);
        rotateRight = action("menu.image.rotate-right", ImagescanGlyph.ROTATE_RIGHT, KeyEvent.VK_R, CTRL,
                presenter::rotateRight);
        rotate180 = action("menu.image.rotate-180", null, 0, 0, presenter::rotate180);
        flipHorizontal = action("menu.image.flip-horizontal", ImagescanGlyph.FLIP_HORIZONTAL, 0, 0,
                presenter::flipHorizontal);
        flipVertical = action("menu.image.flip-vertical", ImagescanGlyph.FLIP_VERTICAL, 0, 0, presenter::flipVertical);
        preview = action("menu.scan.preview", ImagescanGlyph.PREVIEW, KeyEvent.VK_F7, 0, presenter::preview);
        scan = action("menu.scan.scan", ImagescanGlyph.SCAN, KeyEvent.VK_F8, 0, presenter::scan);
        cancel = action("menu.scan.cancel", ImagescanGlyph.CANCEL, KeyEvent.VK_PERIOD, CTRL, presenter::cancelScan);
        refresh = action("menu.scan.refresh", StandardGlyph.REFRESH, KeyEvent.VK_F5, 0, presenter::refreshScanners);
        zoomIn = action("menu.view.zoom-in", StandardGlyph.ZOOM_IN, KeyEvent.VK_PLUS, CTRL, view::zoomIn);
        // --8<-- [end:actions]
        zoomOut = action("menu.view.zoom-out", StandardGlyph.ZOOM_OUT, KeyEvent.VK_MINUS, CTRL, view::zoomOut);
        zoomFit = action("menu.view.zoom-fit", ImagescanGlyph.ZOOM_FIT, KeyEvent.VK_0, CTRL, view::zoomToFit);
        zoomActual = action("menu.view.zoom-actual", ImagescanGlyph.ZOOM_ACTUAL, KeyEvent.VK_1, CTRL,
                view::zoomActualSize);
        update(new MainView.Commands(false, false, false, false, false, false, false, false));
    }

    /** All actions, for tests and key bindings. */
    public List<Action> all() {
        return List.of(open, save, saveAs, print, exit, undo, redo, copy, selectAll, clearSelection, crop,
                rotateLeft, rotateRight, rotate180, flipHorizontal, flipVertical, preview, scan, cancel, refresh,
                zoomIn, zoomOut, zoomFit, zoomActual);
    }

    public void update(MainView.Commands c) {
        boolean idle = !c.scanning() && !c.busy();
        open.setEnabled(idle);
        save.setEnabled(c.canSave() && !c.scanning());
        saveAs.setEnabled(c.canSave() && !c.scanning());
        print.setEnabled(c.hasImage() && idle);
        copy.setEnabled(c.hasImage());
        undo.setEnabled(c.canUndo() && idle);
        redo.setEnabled(c.canRedo() && idle);
        selectAll.setEnabled(c.hasImage());
        clearSelection.setEnabled(c.hasSelection());
        crop.setEnabled(c.canCrop());
        for (Action a : List.of(rotateLeft, rotateRight, rotate180, flipHorizontal, flipVertical)) a.setEnabled(c.canEdit());
        preview.setEnabled(c.canScan());
        scan.setEnabled(c.canScan());
        cancel.setEnabled(c.scanning());
        refresh.setEnabled(idle);
        for (Action a : List.of(zoomIn, zoomOut, zoomFit, zoomActual)) a.setEnabled(c.hasImage());
    }

    private Action action(String key, Glyph glyph, int keyCode, int modifiers, Runnable command) {
        Mnemonics.Text text = Mnemonics.parse(msg.get(key));
        Action action = new AbstractAction(text.text()) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isEnabled()) command.run();
            }
        };
        action.putValue(Action.ACTION_COMMAND_KEY, key);
        if (text.index() >= 0) {
            action.putValue(Action.MNEMONIC_KEY, text.keyCode());
            action.putValue(Action.DISPLAYED_MNEMONIC_INDEX_KEY, text.index());
        }
        String tip = msg.has(key + ".tip") ? msg.get(key + ".tip") : text.text();
        action.putValue(Action.SHORT_DESCRIPTION, tip);
        if (keyCode != 0) action.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(keyCode, modifiers));
        if (glyph != null) {
            action.putValue(Action.SMALL_ICON, new ToolIcon(glyph, 16));
            action.putValue(Action.LARGE_ICON_KEY, new ToolIcon(glyph));
        }
        return action;
    }
}
