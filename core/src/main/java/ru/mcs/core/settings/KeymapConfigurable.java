package ru.mcs.core.settings;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import ru.mcs.core.i18n.Messages;
import ru.mcs.spi.swing.editor.keymap.KeymapService;
import ru.mcs.spi.swing.editor.keymap.ShortcutBinding;
import ru.mcs.spi.swing.editor.keymap.ShortcutScope;
import ru.mcs.spi.swing.editor.keymap.Shortcuts;
import ru.mcs.spi.swing.settings.Configurable;

/**
 * Страница сочетаний клавиш языка: вкладка на каждую область, таблица «Действие — Сочетание».
 * Изменения копятся в черновике и попадают в сервис по «Применить»; конфликты внутри области не допускаются.
 */
final class KeymapConfigurable implements Configurable {

    private final KeymapService service;
    private final List<ShortcutScope> scopes;
    private final Map<String, String> draft = new HashMap<>();
    private final Map<String, String> defaults = new HashMap<>();
    private final List<ScopeModel> models = new ArrayList<>();
    private final List<JTable> tables = new ArrayList<>();
    private final int mask = Shortcuts.defaultMask();
    private Map<String, String> baseline = Map.of();
    private JTabbedPane tabs;
    private JComponent component;

    KeymapConfigurable(KeymapService service, String languageId) {
        this.service = service;
        this.scopes = service.scopes(languageId);
        load();
    }

    @Override
    public JComponent createComponent() {
        if (component != null) {
            return component;
        }
        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        component = root;
        if (scopes.isEmpty()) {
            root.add(new JLabel(Messages.get("settings.keys.empty")), BorderLayout.NORTH);
            return root;
        }
        tabs = new JTabbedPane();
        for (ShortcutScope scope : scopes) {
            ScopeModel model = new ScopeModel(scope);
            JTable table = new JTable(model);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setRowHeight(24);
            table.setFillsViewportHeight(true);
            table.getColumnModel().getColumn(1).setCellRenderer(new ShortcutRenderer(scope));
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    int row = table.rowAtPoint(event.getPoint());
                    if (event.getClickCount() == 2 && row >= 0) {
                        edit(scope, row);
                    }
                }
            });
            models.add(model);
            tables.add(table);
            tabs.addTab(scope.displayName(), new JScrollPane(table));
        }
        JButton edit = new JButton(Messages.get("settings.keys.edit"));
        edit.addActionListener(event -> editSelected());
        JButton reset = new JButton(Messages.get("settings.keys.reset"));
        reset.addActionListener(event -> resetSelected());
        JButton resetAll = new JButton(Messages.get("settings.keys.resetAll"));
        resetAll.addActionListener(event -> resetAll());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(edit);
        buttons.add(reset);
        buttons.add(resetAll);
        root.add(tabs, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
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
        load();
        changed();
    }

    /** Итоговое сочетание в черновике. */
    String shortcutOf(String scopeId, String actionId) {
        String key = KeymapService.key(scopeId, actionId);
        String value = draft.get(key);
        return value != null ? value : defaults.getOrDefault(key, "");
    }

    /** Название действия той же области, которое уже использует это сочетание, либо null. */
    String conflict(String scopeId, String actionId, String shortcut) {
        KeyStroke wanted = parse(shortcut);
        if (wanted == null) {
            return null;
        }
        for (ShortcutScope scope : scopes) {
            if (!scope.id().equals(scopeId)) {
                continue;
            }
            for (ShortcutBinding binding : scope.bindings()) {
                if (!binding.actionId().equals(actionId)
                        && Objects.equals(wanted, parse(shortcutOf(scopeId, binding.actionId())))) {
                    return binding.name();
                }
            }
        }
        return null;
    }

    /** Назначает сочетание (пустая строка — убрать); false, если запись неверна или есть конфликт. */
    boolean setShortcut(String scopeId, String actionId, String shortcut) {
        String key = KeymapService.key(scopeId, actionId);
        if (!defaults.containsKey(key)) {
            return false;
        }
        String value = shortcut == null ? "" : shortcut.trim();
        KeyStroke stroke;
        try {
            stroke = Shortcuts.parse(value, mask);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        if (conflict(scopeId, actionId, value) != null) {
            return false;
        }
        if (Objects.equals(stroke, parse(defaults.get(key)))) {
            draft.remove(key);
        } else {
            draft.put(key, value);
        }
        changed();
        return true;
    }

    void resetShortcut(String scopeId, String actionId) {
        draft.remove(KeymapService.key(scopeId, actionId));
        changed();
    }

    void resetAll() {
        draft.clear();
        changed();
    }

    private void load() {
        defaults.clear();
        for (ShortcutScope scope : scopes) {
            for (ShortcutBinding binding : scope.bindings()) {
                defaults.put(KeymapService.key(scope.id(), binding.actionId()), binding.defaultShortcut());
            }
        }
        Map<String, String> normalized = new HashMap<>();
        for (Map.Entry<String, String> entry : service.overrides().entrySet()) {
            String base = defaults.get(entry.getKey());
            if (base == null || !Objects.equals(parse(entry.getValue()), parse(base))) {
                normalized.put(entry.getKey(), entry.getValue());
            }
        }
        baseline = Map.copyOf(normalized);
        draft.clear();
        draft.putAll(baseline);
    }

    private KeyStroke parse(String shortcut) {
        try {
            return Shortcuts.parse(shortcut, mask);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private String display(String shortcut) {
        KeyStroke stroke = parse(shortcut);
        return stroke == null ? "—" : Shortcuts.display(stroke);
    }

    private void changed() {
        for (ScopeModel model : models) {
            int rows = model.getRowCount();
            if (rows > 0) {
                model.fireTableRowsUpdated(0, rows - 1);
            }
        }
    }

    private void editSelected() {
        int tab = tabs.getSelectedIndex();
        int row = tab < 0 ? -1 : tables.get(tab).getSelectedRow();
        if (row >= 0) {
            edit(scopes.get(tab), row);
        }
    }

    private void resetSelected() {
        int tab = tabs.getSelectedIndex();
        int row = tab < 0 ? -1 : tables.get(tab).getSelectedRow();
        if (row >= 0) {
            resetShortcut(scopes.get(tab).id(), scopes.get(tab).bindings().get(row).actionId());
        }
    }

    private void edit(ShortcutScope scope, int row) {
        ShortcutBinding binding = scope.bindings().get(row);
        String picked = capture(scope, binding);
        if (picked != null) {
            setShortcut(scope.id(), binding.actionId(), picked);
        }
    }

    /** Диалог захвата: возвращает запись сочетания, пустую строку («без сочетания») или null при отмене. */
    private String capture(ShortcutScope scope, ShortcutBinding binding) {
        Window owner = component == null ? null : SwingUtilities.getWindowAncestor(component);
        JDialog dialog = new JDialog(owner, Messages.get("settings.keys.capture.title"),
                Dialog.ModalityType.APPLICATION_MODAL);
        String[] captured = {shortcutOf(scope.id(), binding.actionId())};
        String[] result = {null};
        JTextField field = new JTextField(display(captured[0]), 24);
        field.setEditable(false);
        field.setFocusTraversalKeysEnabled(false);
        JLabel warning = new JLabel(" ");
        warning.setForeground(Color.RED);
        JButton ok = new JButton(Messages.get("settings.keys.ok"));
        JButton clear = new JButton(Messages.get("settings.keys.clear"));
        JButton cancel = new JButton(Messages.get("settings.keys.cancel"));
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                int code = event.getKeyCode();
                if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT
                        || code == KeyEvent.VK_META || code == KeyEvent.VK_ALT_GRAPH
                        || code == KeyEvent.VK_UNDEFINED) {
                    return;
                }
                KeyStroke stroke = KeyStroke.getKeyStroke(code, event.getModifiersEx());
                captured[0] = Shortcuts.format(stroke, mask);
                field.setText(Shortcuts.display(stroke));
                String other = conflict(scope.id(), binding.actionId(), captured[0]);
                warning.setText(other == null ? " " : Messages.get("settings.keys.conflict") + " " + other);
                ok.setEnabled(other == null);
                event.consume();
            }
        });
        ok.addActionListener(event -> {
            result[0] = captured[0];
            dialog.dispose();
        });
        clear.addActionListener(event -> {
            result[0] = "";
            dialog.dispose();
        });
        cancel.addActionListener(event -> dialog.dispose());
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        JLabel title = new JLabel(binding.name());
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        top.add(title, BorderLayout.NORTH);
        top.add(new JLabel(Messages.get("settings.keys.capture.prompt")), BorderLayout.CENTER);
        JPanel middle = new JPanel(new BorderLayout(0, 6));
        middle.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        middle.add(field, BorderLayout.NORTH);
        middle.add(warning, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(clear);
        buttons.add(ok);
        buttons.add(cancel);
        dialog.add(top, BorderLayout.NORTH);
        dialog.add(middle, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(component);
        SwingUtilities.invokeLater(field::requestFocusInWindow);
        dialog.setVisible(true);
        return result[0];
    }

    private final class ScopeModel extends AbstractTableModel {

        private final ShortcutScope scope;

        private ScopeModel(ShortcutScope scope) {
            this.scope = scope;
        }

        @Override
        public int getRowCount() {
            return scope.bindings().size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return Messages.get(column == 0 ? "settings.keys.action" : "settings.keys.shortcut");
        }

        @Override
        public Object getValueAt(int row, int column) {
            ShortcutBinding binding = scope.bindings().get(row);
            return column == 0 ? binding.name() : display(shortcutOf(scope.id(), binding.actionId()));
        }
    }

    private final class ShortcutRenderer extends DefaultTableCellRenderer {

        private final ShortcutScope scope;

        private ShortcutRenderer(ShortcutScope scope) {
            this.scope = scope;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            String key = KeymapService.key(scope.id(), scope.bindings().get(row).actionId());
            label.setFont(label.getFont().deriveFont(draft.containsKey(key) ? Font.BOLD : Font.PLAIN));
            return label;
        }
    }
}
