package ru.mcs.core.plugin;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import ru.mcs.core.i18n.Messages;
import ru.mcs.spi.settings.Configurable;
import ru.mcs.spi.settings.ConfigurableProvider;

/** Встроенная страница Settings → Plugins. */
public final class PluginsConfigurableProvider implements ConfigurableProvider {

    public static final String ID = "core.plugins";

    private final PluginManager manager;
    private final PluginState state;
    private final PluginInstaller installer;
    private final Runnable restartRequired;

    public PluginsConfigurableProvider(PluginManager manager, PluginState state, PluginInstaller installer,
                                       Runnable restartRequired) {
        this.manager = manager;
        this.state = state;
        this.installer = installer;
        this.restartRequired = restartRequired;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return Messages.get("plugins.settings.title");
    }

    @Override
    public Configurable createConfigurable() {
        return new PluginsConfigurable();
    }

    private final class PluginsConfigurable implements Configurable {
        private final PluginTableModel model = new PluginTableModel(state);
        private final JPanel panel = new JPanel(new BorderLayout(8, 8));
        private final JTable table = new JTable(model);
        private final JTextArea details = new JTextArea();
        private final JButton install = new JButton(Messages.get("plugins.install.button"));

        @Override
        public JComponent createComponent() {
            JLabel restartNote = new JLabel(Messages.get("plugins.settings.restart.note"));
            panel.add(restartNote, BorderLayout.NORTH);

            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setAutoCreateRowSorter(true);
            table.getSelectionModel().addListSelectionListener(event -> {
                if (!event.getValueIsAdjusting()) {
                    showDetails();
                }
            });
            details.setEditable(false);
            details.setLineWrap(true);
            details.setWrapStyleWord(true);
            details.setRows(7);
            details.setOpaque(false);

            table.setFillsViewportHeight(true);
            JScrollPane tableScroll = new JScrollPane(table);
            tableScroll.setPreferredSize(new Dimension(560, 180));
            tableScroll.setMinimumSize(new Dimension(200, 100));
            JScrollPane detailsScroll = new JScrollPane(details);
            detailsScroll.setPreferredSize(new Dimension(560, 140));
            detailsScroll.setMinimumSize(new Dimension(200, 100));
            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailsScroll);
            split.setContinuousLayout(true);
            split.setResizeWeight(0.60);
            split.setDividerLocation(180);
            panel.add(split, BorderLayout.CENTER);

            install.addActionListener(event -> chooseAndInstall());
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            actions.add(install);
            panel.add(actions, BorderLayout.SOUTH);
            panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            refresh();
            return panel;
        }

        @Override
        public boolean isModified() {
            return model.isModified();
        }

        @Override
        public void apply() {
            if (model.applyChanges()) {
                restartRequired.run();
            }
        }

        @Override
        public void reset() {
            model.resetChanges();
        }

        private void refresh() {
            try {
                List<PluginDescriptor> discovered = manager.discover();
                model.refresh(discovered, Set.copyOf(manager.loadedIds()), manager.failures());
                if (table.getRowCount() > 0) {
                    table.setRowSelectionInterval(0, 0);
                } else {
                    details.setText(Messages.get("plugins.settings.empty"));
                }
            } catch (Exception e) {
                details.setText(Messages.get("plugins.settings.load.error", e.toString()));
            }
        }

        private void showDetails() {
            int selected = table.getSelectedRow();
            if (selected < 0) {
                details.setText("");
                return;
            }
            int row = table.convertRowIndexToModel(selected);
            PluginDescriptor d = model.descriptorAt(row);
            String permissions = d.permissions().isEmpty()
                    ? Messages.get("plugins.permissions.none") : String.join("\n", d.permissions());
            String dependencies = d.dependsOn().isEmpty()
                    ? Messages.get("plugins.dependencies.none") : String.join(", ", d.dependsOn());
            String failure = model.failureAt(row);
            details.setText(Messages.get("plugins.details", d.name(), d.version(), d.id(), d.module(),
                    d.sdkVersion(), dependencies, permissions)
                    + (failure == null ? "" : "\n\n" + Messages.get("plugins.details.failure", failure)));
            details.setCaretPosition(0);
        }

        private void chooseAndInstall() {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(Messages.get("plugins.install.chooser"));
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            chooser.setFileFilter(new FileNameExtensionFilter(
                    Messages.get("plugins.install.filter"), "zip", "jar"));
            if (chooser.showOpenDialog(panel) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            Path source = chooser.getSelectedFile().toPath();
            install.setEnabled(false);
            new SwingWorker<PluginInstaller.Inspection, Void>() {
                @Override
                protected PluginInstaller.Inspection doInBackground() throws Exception {
                    return installer.inspect(source);
                }

                @Override
                protected void done() {
                    try {
                        PluginInstaller.Inspection inspection = get();
                        boolean replace = installer.isInstalled(inspection.descriptor().id());
                        if (PluginInstallDialog.showDialog(panel, inspection, replace)) {
                            install(inspection);
                        } else {
                            install.setEnabled(true);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        install.setEnabled(true);
                    } catch (Exception e) {
                        install.setEnabled(true);
                        showInstallError(e);
                    }
                }
            }.execute();
        }

        private void install(PluginInstaller.Inspection inspection) {
            new SwingWorker<PluginDescriptor, Void>() {
                @Override
                protected PluginDescriptor doInBackground() throws Exception {
                    return installer.install(inspection);
                }

                @Override
                protected void done() {
                    install.setEnabled(true);
                    try {
                        get();
                        restartRequired.run();
                        refresh();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        showInstallError(e);
                    }
                }
            }.execute();
        }

        private void showInstallError(Exception error) {
            Throwable cause = error instanceof ExecutionException && error.getCause() != null
                    ? error.getCause() : error;
            JOptionPane.showMessageDialog(panel, Messages.get("plugins.install.error", cause.toString()),
                    Messages.get("plugins.install.title"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
