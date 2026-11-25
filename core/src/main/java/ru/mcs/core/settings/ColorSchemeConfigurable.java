package ru.mcs.core.settings;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import ru.mcs.core.i18n.Messages;
import ru.mcs.spi.swing.editor.style.ColorSchemeService;
import ru.mcs.spi.swing.editor.style.TokenDefinition;
import ru.mcs.spi.swing.editor.style.TokenGroup;
import ru.mcs.spi.swing.editor.style.TokenSpan;
import ru.mcs.spi.swing.settings.Configurable;

/**
 * Страница цветовой схемы: слева языки, в центре таблица токенов (светлая и тёмная тема), снизу предпросмотр
 * образца на светлом и тёмном фоне. Изменения копятся в черновике и попадают в сервис по «Применить».
 */
final class ColorSchemeConfigurable implements Configurable {

    static final int LIGHT_BACKGROUND = 0xFFFFFF;
    static final int LIGHT_TEXT = 0x24292F;
    static final int DARK_BACKGROUND = 0x1E222A;
    static final int DARK_TEXT = 0xD8DEE9;

    private final ColorSchemeService service;
    private final Map<String, TokenDefinition> definitions = new HashMap<>();
    private final Map<String, Integer> draft = new HashMap<>();
    private Map<String, Integer> baseline = Map.of();
    private final DefaultListModel<TokenGroup> groups = new DefaultListModel<>();
    private final JList<TokenGroup> groupList = new JList<>(groups);
    private final TokenTableModel model = new TokenTableModel();
    private final JTable table = new JTable(model);
    private final JTextPane lightPreview = preview(LIGHT_BACKGROUND);
    private final JTextPane darkPreview = preview(DARK_BACKGROUND);
    private JComponent component;
    private TokenGroup current;

    ColorSchemeConfigurable(ColorSchemeService service) {
        this.service = service;
        load();
    }

    @Override
    public JComponent createComponent() {
        if (component != null) {
            return component;
        }
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        component = root;
        if (groups.isEmpty()) {
            root.add(new JLabel(Messages.get("settings.colors.empty")), BorderLayout.NORTH);
            return root;
        }
        groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groupList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focused) {
                Object text = value instanceof TokenGroup group ? group.displayName() : value;
                return super.getListCellRendererComponent(list, text, index, selected, focused);
            }
        });
        groupList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                selectGroup(groupList.getSelectedIndex());
            }
        });
        JScrollPane left = new JScrollPane(groupList);
        left.setPreferredSize(new Dimension(150, 100));

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.setDefaultRenderer(Integer.class, new SwatchRenderer());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int row = table.rowAtPoint(event.getPoint());
                int column = table.columnAtPoint(event.getPoint());
                if (row >= 0 && column >= 1) {
                    chooseColor(row, column == 2);
                }
            }
        });

        JButton resetToken = new JButton(Messages.get("settings.colors.resetToken"));
        resetToken.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (current != null && row >= 0) {
                resetToken(current.tokens().get(row).id());
            }
        });
        JButton resetAll = new JButton(Messages.get("settings.colors.resetAll"));
        resetAll.addActionListener(event -> resetAll());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(resetToken);
        buttons.add(resetAll);

        JPanel previews = new JPanel(new GridLayout(1, 2, 8, 0));
        previews.add(titled(lightPreview, "settings.colors.light"));
        previews.add(titled(darkPreview, "settings.colors.dark"));
        previews.setPreferredSize(new Dimension(100, 190));

        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.add(buttons, BorderLayout.NORTH);
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        center.add(previews, BorderLayout.SOUTH);
        root.add(left, BorderLayout.WEST);
        root.add(center, BorderLayout.CENTER);
        groupList.setSelectedIndex(0);
        selectGroup(0);
        return root;
    }

    @Override
    public boolean isModified() {
        return !draft.equals(baseline);
    }

    @Override
    public void apply() {
        service.setOverrides(new HashMap<>(draft));
        baseline = Map.copyOf(draft);
    }

    @Override
    public void reset() {
        String keep = current == null ? null : current.id();
        load();
        int index = 0;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).id().equals(keep)) {
                index = i;
            }
        }
        if (!groups.isEmpty()) {
            groupList.setSelectedIndex(index);
        }
        selectGroup(index);
        changed();
    }

    void selectGroup(int index) {
        if (index < 0 || index >= groups.size()) {
            return;
        }
        current = groups.get(index);
        model.fireTableDataChanged();
        refreshPreviews();
    }

    void setColor(String token, boolean dark, int rgb) {
        TokenDefinition definition = definitions.get(token);
        if (definition == null) {
            return;
        }
        String key = ColorSchemeService.key(token, dark);
        int value = rgb & 0xFFFFFF;
        if (value == definition.rgb(dark)) {
            draft.remove(key);
        } else {
            draft.put(key, value);
        }
        changed();
    }

    void resetToken(String token) {
        draft.remove(ColorSchemeService.key(token, false));
        draft.remove(ColorSchemeService.key(token, true));
        changed();
    }

    void resetAll() {
        draft.clear();
        changed();
    }

    Color previewColorAt(boolean dark, int offset) {
        JTextPane pane = dark ? darkPreview : lightPreview;
        StyledDocument document = pane.getStyledDocument();
        return StyleConstants.getForeground(document.getCharacterElement(offset).getAttributes());
    }

    private void load() {
        groups.clear();
        definitions.clear();
        for (TokenGroup group : service.groups()) {
            groups.addElement(group);
            for (TokenDefinition definition : group.tokens()) {
                definitions.putIfAbsent(definition.id(), definition);
            }
        }
        baseline = normalize(service.overrides());
        draft.clear();
        draft.putAll(baseline);
    }

    /** Значения, равные цвету по умолчанию, не считаются переопределениями; неизвестные токены сохраняются. */
    private Map<String, Integer> normalize(Map<String, Integer> source) {
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String key = entry.getKey();
            boolean dark = key.endsWith(":dark");
            TokenDefinition definition = definitions.get(key.substring(0, key.lastIndexOf(':')));
            if (definition == null || definition.rgb(dark) != entry.getValue()) {
                result.put(key, entry.getValue());
            }
        }
        return Map.copyOf(result);
    }

    private int effective(TokenDefinition definition, boolean dark) {
        return draft.getOrDefault(ColorSchemeService.key(definition.id(), dark), definition.rgb(dark));
    }

    private boolean overridden(TokenDefinition definition, boolean dark) {
        return draft.containsKey(ColorSchemeService.key(definition.id(), dark));
    }

    private void chooseColor(int row, boolean dark) {
        if (current == null || row >= current.tokens().size()) {
            return;
        }
        TokenDefinition definition = current.tokens().get(row);
        Color picked = JColorChooser.showDialog(component, Messages.get("settings.colors.choose"),
                new Color(effective(definition, dark)));
        if (picked != null) {
            setColor(definition.id(), dark, picked.getRGB());
        }
    }

    private void changed() {
        int rows = model.getRowCount();
        if (rows > 0) {
            model.fireTableRowsUpdated(0, rows - 1);
        }
        refreshPreviews();
    }

    private void refreshPreviews() {
        render(lightPreview, false);
        render(darkPreview, true);
    }

    private void render(JTextPane pane, boolean dark) {
        if (current == null) {
            pane.setText("");
            return;
        }
        String sample = current.sample();
        pane.setText(sample);
        StyledDocument document = pane.getStyledDocument();
        SimpleAttributeSet base = new SimpleAttributeSet();
        StyleConstants.setForeground(base, new Color(dark ? DARK_TEXT : LIGHT_TEXT));
        document.setCharacterAttributes(0, document.getLength(), base, true);
        for (TokenSpan span : current.preview().highlight(sample)) {
            TokenDefinition definition = definitions.get(span.token());
            int end = Math.min(span.end(), document.getLength());
            if (definition == null || end <= span.start()) {
                continue;
            }
            SimpleAttributeSet attributes = new SimpleAttributeSet();
            StyleConstants.setForeground(attributes, new Color(effective(definition, dark)));
            document.setCharacterAttributes(span.start(), end - span.start(), attributes, false);
        }
        pane.setCaretPosition(0);
    }

    private static JTextPane preview(int background) {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setBackground(new Color(background));
        pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return pane;
    }

    private static JComponent titled(JTextPane pane, String titleKey) {
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createTitledBorder(Messages.get(titleKey)));
        return scroll;
    }

    private final class TokenTableModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return current == null ? 0 : current.tokens().size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> Messages.get("settings.colors.token");
                case 1 -> Messages.get("settings.colors.light");
                default -> Messages.get("settings.colors.dark");
            };
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? String.class : Integer.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            TokenDefinition definition = current.tokens().get(row);
            return column == 0 ? definition.name() : (Object) effective(definition, column == 2);
        }
    }

    private record Swatch(int rgb) implements Icon {

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            graphics.setColor(new Color(rgb));
            graphics.fillRect(x, y, 14, 14);
            graphics.setColor(Color.GRAY);
            graphics.drawRect(x, y, 13, 13);
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }

    private final class SwatchRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            if (value instanceof Integer rgb && current != null && row < current.tokens().size()) {
                label.setIcon(new Swatch(rgb));
                label.setText(String.format("#%06X", rgb));
                boolean changed = overridden(current.tokens().get(row), column == 2);
                label.setFont(label.getFont().deriveFont(changed ? Font.BOLD : Font.PLAIN));
            }
            return label;
        }
    }
}
