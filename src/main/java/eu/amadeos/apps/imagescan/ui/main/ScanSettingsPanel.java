package eu.amadeos.apps.imagescan.ui.main;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.commons.swing.ToolButtons;
import eu.amadeos.apps.imagescan.domain.ColorMode;
import eu.amadeos.apps.imagescan.domain.ScanSettings;
import eu.amadeos.apps.imagescan.domain.Scanner;
import eu.amadeos.apps.imagescan.domain.ScannerOptions;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The scan settings beside the image, as in XSane and Simple Scan: scanner, mode and resolution, what part
 * of the glass will be scanned and how large the result will be, and the Preview and Scan buttons.
 */
public final class ScanSettingsPanel extends JPanel {
    private final Messages msg;
    private final JComboBox<Scanner> scanners = new JComboBox<>();
    private final JComboBox<ColorMode> modes = new JComboBox<>();
    private final JComboBox<Integer> resolutions = new JComboBox<>();
    private final JTextArea area = wrapping();
    private final JTextArea estimate = wrapping();
    private boolean updating;

    public ScanSettingsPanel(MainPresenter presenter, ScanActions actions, Messages msg) {
        super(new GridBagLayout());
        EdtExecutor.requireEdt();
        this.msg = msg;
        setBorder(BorderFactory.createCompoundBorder(new AmadeUI.MotifBevelBorder(true, 2),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        scanners.setName("scanners");
        scanners.setPrototypeDisplayValue(new Scanner("x", "Canon", "CanoScan LiDE 300", ""));
        scanners.addActionListener(e -> {
            if (!updating && scanners.getSelectedItem() instanceof Scanner s) presenter.selectScanner(s);
        });
        modes.setName("modes");
        modes.setRenderer(renderer(v -> msg.get("mode." + ((ColorMode) v).name().toLowerCase(Locale.ROOT))));
        modes.addActionListener(e -> {
            if (!updating && modes.getSelectedItem() instanceof ColorMode m) presenter.setMode(m);
        });
        resolutions.setName("resolutions");
        resolutions.setRenderer(renderer(v -> msg.format("resolution.dpi", v)));
        resolutions.addActionListener(e -> {
            if (!updating && resolutions.getSelectedItem() instanceof Integer dpi) presenter.setResolution(dpi);
        });
        area.setName("area");
        estimate.setName("estimate");
        estimate.setFont(estimate.getFont().deriveFont(Font.PLAIN, 11f));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 2, 0);

        add(label("settings.scanner", scanners), c);
        c.gridy++;
        JPanel scannerRow = new JPanel(new GridBagLayout());
        GridBagConstraints r = new GridBagConstraints();
        r.fill = GridBagConstraints.HORIZONTAL;
        r.weightx = 1;
        scannerRow.add(scanners, r);
        r.weightx = 0;
        r.insets = new Insets(0, 4, 0, 0);
        scannerRow.add(ToolButtons.button(actions.refresh), r);
        c.insets = new Insets(0, 0, 10, 0);
        add(scannerRow, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 2, 0);
        add(label("settings.mode", modes), c);
        c.gridy++;
        c.insets = new Insets(0, 0, 10, 0);
        add(modes, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 2, 0);
        add(label("settings.resolution", resolutions), c);
        c.gridy++;
        c.insets = new Insets(0, 0, 10, 0);
        add(resolutions, c);

        c.gridy++;
        add(new JSeparator(SwingConstants.HORIZONTAL), c);
        c.gridy++;
        c.insets = new Insets(0, 0, 2, 0);
        add(new JLabel(Mnemonics.strip(msg.get("settings.area"))), c);
        c.gridy++;
        add(area, c);
        c.gridy++;
        c.insets = new Insets(0, 0, 10, 0);
        add(estimate, c);

        c.gridy++;
        JTextArea hint = wrapping();
        hint.setText(msg.get("settings.hint"));
        hint.setName("hint");
        hint.setFont(estimate.getFont());
        add(hint, c);

        c.gridy++;
        c.insets = new Insets(4, 0, 4, 0);
        add(bigButton(actions.preview), c);
        c.gridy++;
        add(bigButton(actions.scan), c);

        c.gridy++;
        c.weighty = 1;
        add(new JPanel(), c);
        setPreferredSize(new Dimension(240, getPreferredSize().height));
    }

    /** Read-only text that wraps at the panel's width, in the label font. */
    private static JTextArea wrapping() {
        JTextArea text = new JTextArea(" ");
        text.setEditable(false);
        text.setFocusable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setBorder(null);
        text.setColumns(18);
        text.setFont(new JLabel().getFont());
        // Label colours, not those of an input field: readable on the panel in dark themes too.
        AmadeUI.style(text, (t, theme) -> t.setForeground(theme.textActive()));
        return text;
    }

    private JLabel label(String key, Component target) {
        JLabel label = Mnemonics.apply(new JLabel(), msg.get(key));
        label.setLabelFor(target);
        return label;
    }

    private static JButton bigButton(javax.swing.Action action) {
        JButton button = new JButton(action);
        button.setName(String.valueOf(action.getValue(javax.swing.Action.ACTION_COMMAND_KEY)));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setIconTextGap(8);
        return button;
    }

    private interface Text {
        String of(Object value);
    }

    private static DefaultListCellRenderer renderer(Text text) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                return super.getListCellRendererComponent(list, value == null ? "" : text.of(value), index,
                        selected, focus);
            }
        };
    }

    public void showScanners(List<Scanner> list, Optional<Scanner> selected) {
        updating = true;
        try {
            scanners.removeAllItems();
            for (Scanner s : list) scanners.addItem(s);
            selected.ifPresent(scanners::setSelectedItem);
            scanners.setEnabled(!list.isEmpty());
        } finally {
            updating = false;
        }
    }

    public void showScanSettings(ScannerOptions options, ScanSettings settings) {
        updating = true;
        try {
            modes.removeAllItems();
            for (ColorMode m : options.colorModes()) modes.addItem(m);
            modes.setSelectedItem(settings.mode());
            resolutions.removeAllItems();
            for (int dpi : options.resolutions()) resolutions.addItem(dpi);
            resolutions.setSelectedItem(settings.resolution());
        } finally {
            updating = false;
        }
    }

    public void showArea(String areaText, String estimateText) {
        area.setText(areaText);
        estimate.setText(estimateText.isEmpty() ? " " : estimateText);
    }

    /** Settings cannot change while a scan runs. */
    public void setScanning(boolean scanning, boolean scannerReady) {
        scanners.setEnabled(!scanning && scanners.getItemCount() > 0);
        modes.setEnabled(!scanning && scannerReady);
        resolutions.setEnabled(!scanning && scannerReady);
    }

    JComboBox<Scanner> scannerBox() {
        return scanners;
    }

    JComboBox<ColorMode> modeBox() {
        return modes;
    }

    JComboBox<Integer> resolutionBox() {
        return resolutions;
    }

    String areaText() {
        return area.getText();
    }
}
