package ru.mcs.markdown.widget;

import java.awt.BorderLayout;
import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import ru.mcs.markdown.api.ObsidianLinkService;
import ru.mcs.spi.service.AtomicFileStore;

/** Живая редактируемая сессия одного embed; разделяется layout-перестроениями одной и той же ссылки. */
final class TransclusionSession {

    private final Path source;
    private final String target;
    private final WidgetRegistry registry;
    private final JPanel panel = new JPanel(new BorderLayout());
    private final JTextArea editor = new JTextArea();
    private final JLabel status = new JLabel();
    private final JButton save = new JButton();
    private volatile TransclusionDocument document;
    private volatile Path targetFile;
    private volatile String fragment;
    private volatile boolean saving;
    private boolean modified;

    TransclusionSession(Path source, String target, WidgetRegistry registry) {
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
        this.registry = Objects.requireNonNull(registry, "registry");
        status.setText(text("transclusion.loading", "Loading…"));
        save.setText(text("transclusion.save", "Save"));
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        editor.setLineWrap(false);
        editor.setEditable(false);
        save.setEnabled(false);
        save.addActionListener(event -> save());
        JButton open = new JButton(text("transclusion.open", "Open original"));
        open.addActionListener(event -> {
            if (registry.links() != null) {
                registry.links().open(source, target, panel);
            }
        });
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(new JLabel(target));
        toolbar.add(open);
        toolbar.add(save);
        toolbar.add(status);
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(editor), BorderLayout.CENTER);
        editor.getInputMap().put(KeyStroke.getKeyStroke("control S"), "transclusion.save");
        editor.getInputMap().put(KeyStroke.getKeyStroke("meta S"), "transclusion.save");
        editor.getActionMap().put("transclusion.save", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                save();
            }
        });
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                changed();
            }
        });
        load();
    }

    JPanel component() {
        return panel;
    }

    int height(int width) {
        int lines = Math.max(3, editor.getLineCount());
        return Math.min(520, Math.max(180, 48 + lines * (editor.getFontMetrics(editor.getFont()).getHeight() + 2)));
    }

    private void load() {
        ObsidianLinkService links = registry.links();
        AtomicFileStore store = registry.fileStore();
        if (links == null || store == null) {
            status.setText(text("transclusion.unavailable", "Project navigation is unavailable"));
            return;
        }
        links.resolve(source, target).thenCompose(resolved -> {
            if (resolved.isEmpty() || !resolved.orElseThrow().markdown()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        text("transclusion.unresolved", "Unresolved note: " + target, target)));
            }
            ObsidianLinkService.Target resolvedTarget = resolved.orElseThrow();
            CompletableFuture<Loaded> result = new CompletableFuture<>();
            registry.executor().execute(() -> {
                try {
                    String text = new String(store.read(resolvedTarget.file()), StandardCharsets.UTF_8);
                    TransclusionDocument loaded = transclusion(text, resolvedTarget.fragment());
                    result.complete(new Loaded(resolvedTarget.file(), resolvedTarget.fragment(), loaded));
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            });
            return result;
        }).whenComplete((loaded, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null) {
                Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null
                        ? error.getCause() : error;
                status.setText(cause.getMessage() == null ? cause.toString() : cause.getMessage());
                registry.invalidate();
                return;
            }
            targetFile = loaded.file;
            fragment = loaded.fragment;
            document = loaded.document;
            editor.setText(loaded.document.editable());
            editor.setCaretPosition(0);
            editor.setEditable(true);
            modified = false;
            save.setEnabled(false);
            status.setText("");
            registry.invalidate();
        }));
    }

    private void changed() {
        if (document == null) {
            return;
        }
        modified = true;
        save.setEnabled(!saving);
        registry.invalidate();
    }

    private void save() {
        TransclusionDocument current = document;
        Path file = targetFile;
        if (current == null || file == null || saving || !modified) {
            return;
        }
        String edited = editor.getText();
        saving = true;
        save.setEnabled(false);
        status.setText(text("transclusion.saving", "Saving…"));
        registry.executor().execute(() -> {
            try {
                String latestText = new String(registry.fileStore().read(file), StandardCharsets.UTF_8);
                TransclusionDocument latest = transclusion(latestText, fragment);
                registry.fileStore().write(file, latest.merge(edited).getBytes(StandardCharsets.UTF_8));
                SwingUtilities.invokeLater(() -> {
                    document = new TransclusionDocument(latest.prefix(), edited, latest.suffix());
                    saving = false;
                    modified = false;
                    save.setEnabled(false);
                    status.setText(text("transclusion.saved", "Saved"));
                });
            } catch (Throwable error) {
                SwingUtilities.invokeLater(() -> {
                    saving = false;
                    save.setEnabled(true);
                    status.setText(error.getMessage() == null ? error.toString() : error.getMessage());
                });
            }
        });
    }

    private static TransclusionDocument transclusion(String source, String fragment) {
        return fragment == null
                ? TransclusionDocument.whole(source)
                : TransclusionDocument.section(source, fragment);
    }

    private String text(String key, String fallback, Object... args) {
        if (registry.i18n() == null) {
            return fallback;
        }
        String value = registry.i18n().get(key, args);
        return key.equals(value) ? fallback : value;
    }

    private record Loaded(Path file, String fragment, TransclusionDocument document) {
    }
}
