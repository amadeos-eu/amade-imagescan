package org.datazuul.amadeos.imagescan.ui.main;

import org.datazuul.amadeos.commons.i18n.Language;
import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.commons.i18n.Mnemonics;
import org.datazuul.amadeos.commons.i18n.TranslationCheck;
import org.datazuul.amadeos.imagescan.bootstrap.ImagescanModule;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationTest {
    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(ImagescanModule.class, ImagescanModule.MESSAGES));
    }

    @Test
    void textsAreFormattedInTheLanguage() {
        Messages german = Messages.load(ImagescanModule.class, ImagescanModule.MESSAGES, Locale.GERMANY);
        assertEquals("Ganze Glasfläche (216 × 297 mm)", german.format("area.glass.size", 216, 297));
        // No thousands separator in pixel counts.
        assertEquals("Gescannt: 2480 × 3508 px.", german.format("status.scanned", 2480, 3508));
        Messages french = Messages.load(ImagescanModule.class, ImagescanModule.MESSAGES, Locale.FRANCE);
        assertEquals("À propos d'amade-imagescan", french.get("menu.help.about"));
        assertEquals("Impossible d'ouvrir a.png : b", french.format("error.open", "a.png", "b"));
        Messages italian = Messages.load(ImagescanModule.class, ImagescanModule.MESSAGES, Locale.ITALIAN);
        assertEquals("300 dpi", italian.format("resolution.dpi", 300));
    }

    /** Within the menu bar and within each menu, no two entries share a mnemonic letter. */
    @Test
    void mnemonicsAreUniquePerMenu() {
        Map<String, List<String>> menus = Map.of(
                "bar", List.of("menu.file", "menu.edit", "menu.image", "menu.scan", "menu.view", "menu.options", "menu.help"),
                "file", List.of("menu.file.open", "menu.file.save", "menu.file.save-as", "menu.file.print", "menu.file.exit"),
                "edit", List.of("menu.edit.undo", "menu.edit.redo", "menu.edit.copy", "menu.edit.select-all",
                        "menu.edit.select-none"),
                "image", List.of("menu.image.crop", "menu.image.rotate-left", "menu.image.rotate-right",
                        "menu.image.rotate-180", "menu.image.flip-horizontal", "menu.image.flip-vertical"),
                "scan", List.of("menu.scan.preview", "menu.scan.scan", "menu.scan.cancel", "menu.scan.refresh"),
                "view", List.of("menu.view.zoom-in", "menu.view.zoom-out", "menu.view.zoom-fit", "menu.view.zoom-actual"),
                "settings", List.of("settings.scanner", "settings.mode", "settings.resolution"));
        Map<String, String> clashes = new HashMap<>();
        for (Language language : Language.values()) {
            Messages messages = Messages.load(ImagescanModule.class, ImagescanModule.MESSAGES, language.locale());
            for (Map.Entry<String, List<String>> menu : menus.entrySet()) {
                Map<Character, String> seen = new HashMap<>();
                for (String key : menu.getValue()) {
                    Mnemonics.Text t = Mnemonics.parse(messages.get(key));
                    if (t.index() < 0) continue;
                    char letter = Character.toLowerCase(t.text().charAt(t.index()));
                    String other = seen.put(letter, key);
                    if (other != null) clashes.put(language.code() + " " + key, other);
                }
            }
        }
        assertEquals(Map.of(), clashes);
    }
}
