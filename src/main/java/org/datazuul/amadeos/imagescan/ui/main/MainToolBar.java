package org.datazuul.amadeos.imagescan.ui.main;

import org.datazuul.amadeos.commons.swing.EdtExecutor;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JToolBar;
import javax.swing.border.BevelBorder;
import java.util.List;

/** Open, save, print; preview, scan, cancel; undo, redo; crop, turn, mirror; zoom. */
public final class MainToolBar extends JToolBar {
    public MainToolBar(ScanActions actions) {
        EdtExecutor.requireEdt();
        setFloatable(false);
        setRollover(false);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED),
                BorderFactory.createEmptyBorder(2, 3, 2, 3)));
        group(List.of(actions.open, actions.save, actions.print));
        group(List.of(actions.preview, actions.scan, actions.cancel));
        group(List.of(actions.undo, actions.redo));
        group(List.of(actions.crop, actions.rotateLeft, actions.rotateRight, actions.flipHorizontal,
                actions.flipVertical));
        for (Action a : List.of(actions.zoomOut, actions.zoomIn, actions.zoomFit, actions.zoomActual)) {
            add(ToolButtons.button(a));
        }
    }

    private void group(List<Action> group) {
        for (Action a : group) add(ToolButtons.button(a));
        addSeparator();
    }
}
