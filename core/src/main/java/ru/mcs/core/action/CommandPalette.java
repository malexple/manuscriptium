package ru.mcs.core.action;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.action.ActionPlaces;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.action.DataContext;

/** Палитра действий поверх ActionManager: Ctrl+Shift+A, поиск, Enter — выполнить, Esc — закрыть. */
public final class CommandPalette {

    public static final String ACTION_ID = "core.findAction";

    private CommandPalette() {
    }

    public static void install(
            JFrame frame,
            ActionManagerImpl actions,
            Supplier<DataContext> context
    ) {
        actions.registerAction(
                ACTION_ID,
                new AnAction("Find Action...", "Search and run an action", null) {
                    @Override
                    public void actionPerformed(AnActionEvent e) {
                        show(frame, actions, context);
                    }
                }
        );
        actions.addToGroup(ActionIds.TOOLS_MENU, ACTION_ID, Constraints.LAST);

        KeyStroke shortcut = KeyStroke.getKeyStroke(
                KeyEvent.VK_A,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK
        );
        frame.getRootPane().registerKeyboardAction(
                e -> show(frame, actions, context),
                shortcut,
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private static void show(
            JFrame frame,
            ActionManagerImpl actions,
            Supplier<DataContext> context
    ) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> show(frame, actions, context));
            return;
        }
        new PaletteDialog(
                frame,
                new CommandPaletteModel(actions, context)
        ).setVisible(true);
    }

    private static final class PaletteDialog extends JDialog {

        private final CommandPaletteModel model;
        private final JTextField search = new JTextField();
        private final DefaultListModel<CommandPaletteModel.Entry> entries =
                new DefaultListModel<>();
        private final JList<CommandPaletteModel.Entry> list = new JList<>(entries);
        private final JLabel status = new JLabel(" ");

        PaletteDialog(JFrame owner, CommandPaletteModel model) {
            super(owner, "Find Action", true);
            this.model = model;

            setUndecorated(true);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);

            JPanel content = new JPanel(new BorderLayout(0, 8));
            content.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderColor()),
                    BorderFactory.createEmptyBorder(12, 12, 10, 12)
            ));

            search.putClientProperty(
                    "JTextField.placeholderText",
                    "Type action name or id"
            );
            content.add(search, BorderLayout.NORTH);

            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer(new EntryRenderer());
            list.setVisibleRowCount(12);
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2
                            && list.locationToIndex(e.getPoint()) >= 0) {
                        runSelected();
                    }
                }
            });

            content.add(new JScrollPane(list), BorderLayout.CENTER);

            status.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
            content.add(status, BorderLayout.SOUTH);
            setContentPane(content);

            search.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    reload();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    reload();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    reload();
                }
            });

            search.addActionListener(e -> runSelected());

            bind(
                    search,
                    KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
                    () -> moveSelection(1)
            );
            bind(
                    search,
                    KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
                    () -> moveSelection(-1)
            );
            bind(
                    getRootPane(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    this::dispose
            );

            setSize(new Dimension(640, 420));
            setLocation(
                    owner.getX() + (owner.getWidth() - getWidth()) / 2,
                    owner.getY() + Math.min(
                            100,
                            Math.max(20, owner.getHeight() / 5)
                    )
            );

            reload();
        }

        private void reload() {
            String selectedId = list.getSelectedValue() == null
                    ? null
                    : list.getSelectedValue().id();

            List<CommandPaletteModel.Entry> found =
                    model.search(search.getText());

            entries.clear();
            found.forEach(entries::addElement);

            int selected = 0;
            if (selectedId != null) {
                for (int i = 0; i < found.size(); i++) {
                    if (found.get(i).id().equals(selectedId)) {
                        selected = i;
                        break;
                    }
                }
            }

            if (!found.isEmpty()) {
                list.setSelectedIndex(selected);
                list.ensureIndexIsVisible(selected);
            }

            status.setText(
                    found.size()
                            + (found.size() == 1 ? " action" : " actions")
                            + "    Enter — run    Esc — close"
            );
        }

        private void moveSelection(int delta) {
            if (entries.isEmpty()) {
                return;
            }

            int next = Math.max(
                    0,
                    Math.min(
                            entries.size() - 1,
                            list.getSelectedIndex() + delta
                    )
            );

            list.setSelectedIndex(next);
            list.ensureIndexIsVisible(next);
        }

        private void runSelected() {
            CommandPaletteModel.Entry entry = list.getSelectedValue();
            if (entry == null || !entry.enabled()) {
                return;
            }

            dispose();
            SwingUtilities.invokeLater(() -> model.perform(entry));
        }

        private static void bind(
                JComponent component,
                KeyStroke key,
                Runnable action
        ) {
            component.registerKeyboardAction(
                    e -> action.run(),
                    key,
                    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
            );
        }

        private static Color borderColor() {
            Color color = UIManager.getColor("Component.borderColor");
            return color == null ? Color.GRAY : color;
        }
    }

    private static final class EntryRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean selected,
                boolean focused
        ) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list,
                    value,
                    index,
                    selected,
                    focused
            );

            CommandPaletteModel.Entry entry =
                    (CommandPaletteModel.Entry) value;

            label.setText(entry.text() + "    " + entry.id());
            label.setIcon(entry.icon());
            label.setEnabled(entry.enabled());
            label.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            label.setToolTipText(entry.description());

            return label;
        }
    }
}