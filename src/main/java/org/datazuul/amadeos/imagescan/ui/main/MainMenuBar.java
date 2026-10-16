package org.datazuul.amadeos.imagescan.ui.main;

import org.datazuul.amadeos.commons.i18n.Language;
import org.datazuul.amadeos.commons.i18n.LocaleSettings;
import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.commons.i18n.Mnemonics;
import org.datazuul.amadeos.commons.swing.EdtExecutor;
import org.datazuul.amadeos.imagescan.repository.ImagescanPreferences.Bar;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.border.BevelBorder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** File, Edit, Image, Scan, View, Options and Help. */
public final class MainMenuBar extends JMenuBar {
    private final Messages msg;

    public MainMenuBar(MainPresenter presenter, ScanActions actions, Messages msg, LocaleSettings locales) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

        JMenu file = menu("menu.file");
        file.add(item(actions.open));
        file.add(item(actions.save));
        file.add(item(actions.saveAs));
        file.addSeparator();
        file.add(item(actions.print));
        file.addSeparator();
        file.add(item(actions.exit));

        JMenu edit = menu("menu.edit");
        edit.add(item(actions.undo));
        edit.add(item(actions.redo));
        edit.addSeparator();
        edit.add(item(actions.copy));
        edit.addSeparator();
        edit.add(item(actions.selectAll));
        edit.add(item(actions.clearSelection));

        JMenu image = menu("menu.image");
        image.add(item(actions.crop));
        image.addSeparator();
        image.add(item(actions.rotateLeft));
        image.add(item(actions.rotateRight));
        image.add(item(actions.rotate180));
        image.addSeparator();
        image.add(item(actions.flipHorizontal));
        image.add(item(actions.flipVertical));

        JMenu scan = menu("menu.scan");
        scan.add(item(actions.preview));
        scan.add(item(actions.scan));
        scan.add(item(actions.cancel));
        scan.addSeparator();
        scan.add(item(actions.refresh));

        JMenu view = menu("menu.view");
        for (Bar bar : Bar.values()) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(msg.get(barKey(bar)), presenter.isBarVisible(bar));
            item.setName(barKey(bar));
            item.addActionListener(e -> presenter.setBarVisible(bar, item.isSelected()));
            view.add(item);
        }
        view.addSeparator();
        view.add(item(actions.zoomIn));
        view.add(item(actions.zoomOut));
        view.add(item(actions.zoomFit));
        view.add(item(actions.zoomActual));

        JMenu options = menu("menu.options");
        if (locales != null) options.add(languageMenu(presenter, locales));

        JMenu help = menu("menu.help");
        JMenuItem about = new JMenuItem(msg.get("menu.help.about"));
        about.setName("menu.help.about");
        about.addActionListener(e -> presenter.about());
        help.add(about);
    }

    static String barKey(Bar bar) {
        return switch (bar) {
            case TOOLBAR -> "menu.view.toolbar";
            case SETTINGS -> "menu.view.settings";
            case STATUS_BAR -> "menu.view.status-bar";
        };
    }

    /** "Desktop setting (Deutsch)" and the five languages; the choice applies at the next start. */
    private JMenu languageMenu(MainPresenter presenter, LocaleSettings locales) {
        JMenu menu = new JMenu(msg.get("menu.options.language"));
        Messages commons = Messages.commons(msg.locale());
        List<Optional<Language>> choices = new ArrayList<>();
        choices.add(Optional.empty());
        for (Language language : Language.values()) choices.add(Optional.of(language));
        ButtonGroup group = new ButtonGroup();
        Optional<Language> current = locales.applicationLanguage();
        for (Optional<Language> choice : choices) {
            String label = choice.map(Language::nativeName).orElseGet(() -> commons.format("language.follow-desktop",
                    locales.desktopResolution().language().nativeName()));
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, choice.equals(current));
            item.setName(choice.map(Language::code).orElse(LocaleSettings.FOLLOW_DESKTOP));
            item.addActionListener(e -> presenter.setLanguage(choice));
            group.add(item);
            menu.add(item);
            if (choice.isEmpty()) menu.addSeparator();
        }
        return menu;
    }

    private JMenu menu(String key) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get(key));
        menu.setName(key);
        add(menu);
        return menu;
    }

    private static JMenuItem item(Action action) {
        JMenuItem item = new JMenuItem(action);
        item.setName(String.valueOf(action.getValue(Action.ACTION_COMMAND_KEY)));
        // Menus show text; the small icons would crowd them.
        item.setIcon(null);
        return item;
    }
}
