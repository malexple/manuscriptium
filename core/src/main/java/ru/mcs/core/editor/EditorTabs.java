package ru.mcs.core.editor;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.core.action.ActionMenus;
import ru.mcs.core.i18n.Messages;
import ru.mcs.spi.swing.action.ActionGroup;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.action.AnActionEvent;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.action.ActionPlaces;
import ru.mcs.spi.action.DataContext;

/** Вкладки редактора: закрепление, индикатор изменений, контекстное меню из группы EditorTabPopup. */
public final class EditorTabs extends JPanel {

    private static final Icon PIN = new PinIcon();

    private final FileEditorManagerImpl manager;
    private final ActionManagerImpl actions;
    private final Supplier<DataContext> ctx;
    private final CardLayout cards = new CardLayout();
    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final List<Path> files = new ArrayList<>();
    private boolean syncing;

    public EditorTabs(FileEditorManagerImpl manager, ActionManagerImpl actions, Supplier<DataContext> ctx) {
        this.manager = manager;
        this.actions = actions;
        this.ctx = ctx;
        setLayout(cards);
        add(createEmptyState(), "empty");
        add(tabs, "tabs");

        tabs.addChangeListener(e -> {
            if (!syncing && tabs.getSelectedIndex() >= 0 && tabs.getSelectedIndex() < files.size()) {
                manager.selectFile(files.get(tabs.getSelectedIndex()));
            }
        });
        manager.addListener(new FileEditorManagerImpl.Listener() {
            @Override
            public void tabsChanged() {
                onEdt(EditorTabs.this::rebuild);
            }

            @Override
            public void selectionChanged() {
                onEdt(EditorTabs.this::syncSelection);
            }

            @Override
            public void stateChanged() {
                onEdt(EditorTabs.this::refreshHeaders);
            }
        });
        rebuild();
    }

    private JPanel createEmptyState() {
        JPanel center = new JPanel(new GridBagLayout());
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.add(new JLabel(Messages.get("editor.empty"), SwingConstants.CENTER), BorderLayout.NORTH);
        JPanel buttons = new JPanel(new GridLayout(0, 1, 8, 8));
        addStartButton(buttons, "editor.empty.newFile", "file.new", "core.file.new");
        addStartButton(buttons, "editor.empty.newProject", "project.new", "core.project.new");
        addStartButton(buttons, "editor.empty.openProject", "project.open", "core.project.open");
        addStartButton(buttons, "editor.empty.openFile", "file.open", "core.file.open");
        content.add(buttons, BorderLayout.CENTER);
        center.add(content);
        return center;
    }

    private void addStartButton(JPanel buttons, String name, String textKey, String actionId) {
        JButton button = new JButton(Messages.get(textKey));
        button.setName(name);
        button.addActionListener(e -> runStartAction(actionId));
        buttons.add(button);
    }

    private void runStartAction(String actionId) {
        AnAction action = actions.getAction(actionId);
        if (action == null) {
            return;
        }
        AnActionEvent event = new AnActionEvent(action.getTemplatePresentation().copy(),
                ActionPlaces.MAIN_MENU, ctx.get());
        action.update(event);
        if (event.getPresentation().isEnabled()) {
            action.actionPerformed(event);
        }
    }

    private void rebuild() {
        syncing = true;
        try {
            tabs.removeAll();
            files.clear();
            for (FileEditorManagerImpl.TabInfo info : manager.tabs()) {
                tabs.addTab(null, info.editor().getComponent());
                int index = tabs.getTabCount() - 1;
                files.add(info.file());
                tabs.setToolTipTextAt(index, info.file().toString());
                tabs.setTabComponentAt(index, new TabHeader(info));
            }
            selectCurrent();
            cards.show(this, files.isEmpty() ? "empty" : "tabs");
        } finally {
            syncing = false;
        }
    }

    private void syncSelection() {
        syncing = true;
        try {
            selectCurrent();
        } finally {
            syncing = false;
        }
    }

    private void selectCurrent() {
        int index = manager.getSelectedFile().map(files::indexOf).orElse(-1);
        if (index >= 0 && index < tabs.getTabCount()) {
            tabs.setSelectedIndex(index);
        }
    }

    private void refreshHeaders() {
        List<FileEditorManagerImpl.TabInfo> infos = manager.tabs();
        for (int i = 0; i < tabs.getTabCount() && i < infos.size(); i++) {
            if (tabs.getTabComponentAt(i) instanceof TabHeader header) {
                header.update(infos.get(i));
            }
        }
    }

    private static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    private final class TabHeader extends JPanel {
        private final JLabel label = new JLabel();
        private final JButton close = new JButton("\u00D7");
        private Path file;
        private boolean pinned;

        TabHeader(FileEditorManagerImpl.TabInfo info) {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            setOpaque(false);
            close.setFocusable(false);
            close.setToolTipText(Messages.get("tab.close.tooltip"));
            close.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            close.addActionListener(e -> manager.closeFileAsync(file));
            add(label);
            add(close);

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    manager.selectFile(file);
                    if (e.isPopupTrigger()) {
                        showPopup(e);
                    } else if (SwingUtilities.isMiddleMouseButton(e) && !pinned) {
                        manager.closeFileAsync(file);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showPopup(e);
                    }
                }
            };
            addMouseListener(mouse);
            label.addMouseListener(mouse);
            update(info);
        }

        void update(FileEditorManagerImpl.TabInfo info) {
            this.file = info.file();
            this.pinned = info.pinned();
            label.setText(info.editor().getName() + (info.editor().isModified() ? " *" : ""));
            label.setIcon(info.pinned() ? PIN : null);
            close.setVisible(!info.pinned());
        }

        private void showPopup(MouseEvent e) {
            if (actions.getAction(ActionIds.EDITOR_TAB_POPUP) instanceof ActionGroup group) {
                JPopupMenu popup = ActionMenus.createPopup(group, ActionPlaces.POPUP, ctx);
                if (popup.getComponentCount() > 0) {
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        }
    }

    private static final class PinIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c.getForeground());
                g2.fillOval(x, y + 1, 6, 6);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return 8;
        }

        @Override
        public int getIconHeight() {
            return 8;
        }
    }
}
