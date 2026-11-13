package ru.mcs.plugin.properties;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import ru.mcs.spi.PluginContext;

/** Виджеты небольшого управляемого подмножества front matter активной заметки. */
final class PropertiesPanel extends JPanel {

    private final PluginContext ctx;
    private final JLabel fileLabel = new JLabel();
    private final JTextField titleField = new JTextField();
    private final JTextField tagsField = new JTextField();
    private final JCheckBox draftBox;
    private final JTextArea otherArea = new JTextArea(7, 20);
    private final JLabel statusLabel = new JLabel();
    private final JButton refreshButton;
    private final JButton applyButton;
    private Path file;
    private long generation;
    private boolean applying;
    private boolean busy;

    PropertiesPanel(PluginContext context) {
        super(new BorderLayout(6, 6));
        this.ctx = context;
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        titleField.setName("properties.title");
        tagsField.setName("properties.tags");
        tagsField.setToolTipText(ctx.i18n().get("properties.tags.hint"));
        draftBox = new JCheckBox(ctx.i18n().get("properties.draft"));
        draftBox.setName("properties.draft");
        otherArea.setName("properties.other");
        otherArea.setEditable(false);
        otherArea.setLineWrap(true);
        otherArea.setWrapStyleWord(true);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        form.add(new JLabel(ctx.i18n().get("properties.title")), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(titleField, c);
        c.gridy++;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        form.add(new JLabel(ctx.i18n().get("properties.tags")), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(tagsField, c);
        c.gridy++;
        c.gridx = 1;
        form.add(draftBox, c);

        JPanel north = new JPanel(new BorderLayout());
        north.add(fileLabel, BorderLayout.NORTH);
        north.add(form, BorderLayout.CENTER);

        JPanel other = new JPanel(new BorderLayout(3, 3));
        other.add(new JLabel(ctx.i18n().get("properties.other")), BorderLayout.NORTH);
        other.add(new JScrollPane(otherArea), BorderLayout.CENTER);

        refreshButton = new JButton(ctx.i18n().get("properties.refresh"));
        refreshButton.setName("properties.refresh");
        refreshButton.addActionListener(event -> refresh());
        applyButton = new JButton(ctx.i18n().get("properties.apply"));
        applyButton.setName("properties.apply");
        applyButton.addActionListener(event -> apply());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.add(refreshButton);
        buttons.add(applyButton);
        JPanel south = new JPanel(new BorderLayout());
        south.add(statusLabel, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);
        add(other, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        showFile(ctx.editors().getSelectedFile().filter(PropertiesPanel::isMarkdown).orElse(null));
    }

    void showActive(Path active) {
        if (!applying) {
            showFile(isMarkdown(active) ? active : null);
        }
    }

    void refresh() {
        if (!applying) {
            showFile(file);
        }
    }

    private void showFile(Path selected) {
        generation++;
        file = selected;
        clearWidgets();
        fileLabel.setText(selected == null ? "" : selected.getFileName().toString());
        fileLabel.setToolTipText(selected == null ? null : selected.toString());
        if (selected == null) {
            busy = false;
            statusLabel.setText(ctx.i18n().get("properties.no.file"));
            updateEnabled();
            return;
        }
        load(selected, generation);
    }

    private void load(Path expected, long request) {
        busy = true;
        statusLabel.setText(ctx.i18n().get("properties.loading"));
        updateEnabled();
        new SwingWorker<FrontMatter, Void>() {
            @Override
            protected FrontMatter doInBackground() throws Exception {
                return FrontMatter.parse(MarkdownFileStore.read(expected));
            }

            @Override
            protected void done() {
                if (request != generation || !expected.equals(file)) {
                    return;
                }
                busy = false;
                try {
                    FrontMatter document = get();
                    showValues(document.values());
                    otherArea.setText(document.unknownYaml());
                    otherArea.setCaretPosition(0);
                    statusLabel.setText(ctx.i18n().get(document.present()
                            ? "properties.loaded" : "properties.no.frontmatter"));
                } catch (CancellationException e) {
                    // Актуальный запрос имеет новый generation.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError(e);
                } catch (ExecutionException e) {
                    showError(e.getCause());
                } finally {
                    updateEnabled();
                }
            }
        }.execute();
    }

    private void apply() {
        if (file == null || busy || applying) {
            return;
        }
        final Path expected = file;
        final FrontMatter.Values values = new FrontMatter.Values(titleField.getText().trim(),
                Arrays.stream(tagsField.getText().split(","))
                        .map(String::trim).filter(tag -> !tag.isEmpty()).distinct().toList(),
                draftBox.isSelected());
        applying = true;
        busy = true;
        generation++;
        statusLabel.setText(ctx.i18n().get("properties.saving"));
        updateEnabled();
        ctx.editors().saveAllAsync().whenComplete((ignored, error) -> {
            if (error != null) {
                applying = false;
                busy = false;
                showError(error.getCause() == null ? error : error.getCause());
                updateEnabled();
                return;
            }
            writeFrontMatter(expected, values);
        });
    }

    private void writeFrontMatter(Path expected, FrontMatter.Values values) {
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                String original = MarkdownFileStore.read(expected);
                String updated = FrontMatter.parse(original).update(values);
                if (updated.equals(original)) {
                    return false;
                }
                MarkdownFileStore.writeAtomically(expected, updated);
                return true;
            }

            @Override
            protected void done() {
                boolean changed = false;
                try {
                    changed = get();
                    if (changed && expected.equals(ctx.editors().getSelectedFile().orElse(null))) {
                        if (!ctx.editors().closeFile(expected) || !ctx.editors().openFile(expected)) {
                            throw new IOException(ctx.i18n().get("properties.reload.error"));
                        }
                    }
                    String message = ctx.i18n().get(changed ? "properties.applied" : "properties.unchanged");
                    statusLabel.setText(message);
                    ctx.statusBar().setMessage(message);
                } catch (CancellationException e) {
                    // Apply не отменяется автоматически сменой вкладки.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError(e);
                } catch (ExecutionException e) {
                    showError(e.getCause());
                } catch (IOException e) {
                    showError(e);
                } finally {
                    applying = false;
                    showFile(ctx.editors().getSelectedFile().filter(PropertiesPanel::isMarkdown).orElse(null));
                }
            }
        }.execute();
    }

    private void showValues(FrontMatter.Values values) {
        titleField.setText(values.title());
        tagsField.setText(String.join(", ", values.tags()));
        draftBox.setSelected(values.draft());
    }

    private void clearWidgets() {
        showValues(FrontMatter.Values.empty());
        otherArea.setText("");
    }

    private void updateEnabled() {
        boolean enabled = file != null && !busy && !applying;
        titleField.setEnabled(enabled);
        tagsField.setEnabled(enabled);
        draftBox.setEnabled(enabled);
        applyButton.setEnabled(enabled);
        refreshButton.setEnabled(file != null && !busy && !applying);
    }

    private void showError(Throwable error) {
        String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        statusLabel.setText(ctx.i18n().get("properties.error") + ": " + detail);
        ctx.notifications().error(ctx.i18n().get("toolwindow.properties"), statusLabel.getText());
    }

    private static boolean isMarkdown(Path path) {
        return path != null && path.getFileName() != null
                && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".md");
    }
}
