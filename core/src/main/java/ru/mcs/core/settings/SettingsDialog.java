package ru.mcs.core.settings;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.ScrollPaneConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import ru.mcs.core.i18n.Messages;
import ru.mcs.spi.settings.Configurable;
import ru.mcs.spi.settings.ConfigurableProvider;
import ru.mcs.spi.settings.SettingsManager;

/** Модальный диалог настроек. Страница и её плагин создаются только при первом выборе в дереве. */
final class SettingsDialog extends JDialog {

    private static final System.Logger LOG = System.getLogger(SettingsDialog.class.getName());
    private static final String EMPTY = "$empty";

    private final Map<String, Configurable> configurables = new HashMap<>();
    private final Map<String, JComponent> components = new HashMap<>();
    private final JPanel page = new JPanel(new CardLayout());
    private final JTree tree;
    private boolean applied;

    static boolean showDialog(Window owner, SettingsManager settings) {
        SettingsDialog dialog = new SettingsDialog(owner, settings);
        dialog.setVisible(true);
        return dialog.applied;
    }

    private SettingsDialog(Window owner, SettingsManager settings) {
        super(owner, Messages.get("settings.title"), ModalityType.APPLICATION_MODAL);

        tree = new JTree(SettingsTree.create(settings.getProviders()));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                          boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                if (value instanceof DefaultMutableTreeNode node
                        && node.getUserObject() instanceof ConfigurableProvider provider) {
                    setText(provider.getDisplayName());
                }
                return this;
            }
        };
        renderer.setLeafIcon(null);
        renderer.setOpenIcon(null);
        renderer.setClosedIcon(null);
        tree.setCellRenderer(renderer);
        tree.addTreeSelectionListener(e -> showSelectedPage());

        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setMinimumSize(new Dimension(180, 200));
        JScrollPane pageScroll = new JScrollPane(page,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        pageScroll.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, pageScroll);
        split.setContinuousLayout(true);
        split.setDividerLocation(230);
        add(split, BorderLayout.CENTER);
        add(createButtons(), BorderLayout.SOUTH);

        page.add(new JLabel(Messages.get("settings.empty"), JLabel.CENTER), EMPTY);
        ((CardLayout) page.getLayout()).show(page, EMPTY);
        selectFirstPage();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(860, 540);
        setLocationRelativeTo(owner);
    }

    private JPanel createButtons() {
        JButton ok = new JButton(Messages.get("settings.ok"));
        JButton apply = new JButton(Messages.get("settings.apply"));
        JButton reset = new JButton(Messages.get("settings.reset"));
        JButton cancel = new JButton(Messages.get("settings.cancel"));

        ok.addActionListener(e -> {
            if (applyAll()) {
                dispose();
            }
        });
        apply.addActionListener(e -> applyAll());
        reset.addActionListener(e -> resetSelected());
        cancel.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        buttons.add(reset);
        buttons.add(cancel);
        buttons.add(apply);
        buttons.add(ok);
        getRootPane().setDefaultButton(ok);
        return buttons;
    }

    private void selectFirstPage() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        if (root.getChildCount() > 0) {
            DefaultMutableTreeNode first = (DefaultMutableTreeNode) root.getFirstChild();
            tree.setSelectionPath(new TreePath(first.getPath()));
        }
    }

    private ConfigurableProvider selectedProvider() {
        TreePath path = tree.getSelectionPath();
        if (path == null || !(path.getLastPathComponent() instanceof DefaultMutableTreeNode node)
                || !(node.getUserObject() instanceof ConfigurableProvider provider)) {
            return null;
        }
        return provider;
    }

    private void showSelectedPage() {
        ConfigurableProvider provider = selectedProvider();
        if (provider == null) {
            ((CardLayout) page.getLayout()).show(page, EMPTY);
            return;
        }
        String id = provider.getId();
        JComponent component = components.get(id);
        if (component == null) {
            try {
                Configurable configurable = provider.createConfigurable();
                if (configurable == null) {
                    throw new IllegalStateException("Provider returned null Configurable");
                }
                component = configurable.createComponent();
                if (component == null) {
                    throw new IllegalStateException("Configurable returned null component");
                }
                configurables.put(id, configurable);
                components.put(id, component);
                page.add(component, id);
            } catch (Throwable t) {
                LOG.log(System.Logger.Level.ERROR, "Cannot create settings page: " + id, t);
                component = new javax.swing.JTextArea(Messages.get("settings.page.error", t.toString()));
                ((javax.swing.JTextArea) component).setEditable(false);
                ((javax.swing.JTextArea) component).setLineWrap(true);
                components.put(id, component);
                page.add(component, id);
            }
        }
        ((CardLayout) page.getLayout()).show(page, id);
    }

    private boolean applyAll() {
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, Configurable> entry : configurables.entrySet()) {
            try {
                Configurable configurable = entry.getValue();
                if (configurable.isModified()) {
                    configurable.apply();
                    applied = true;
                }
            } catch (Exception e) {
                LOG.log(System.Logger.Level.ERROR, "Cannot apply settings page: " + entry.getKey(), e);
                errors.add(entry.getKey() + ": " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.get("settings.apply.error", String.join("\n", errors)),
                    Messages.get("settings.title"), JOptionPane.ERROR_MESSAGE);
        }
        return errors.isEmpty();
    }

    private void resetSelected() {
        ConfigurableProvider provider = selectedProvider();
        if (provider == null) {
            return;
        }
        Configurable configurable = configurables.get(provider.getId());
        if (configurable != null) {
            configurable.reset();
        }
    }

    @Override
    public void dispose() {
        for (Configurable configurable : configurables.values()) {
            try {
                configurable.disposeUIResources();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Cannot dispose settings page", e);
            }
        }
        configurables.clear();
        super.dispose();
    }
}
