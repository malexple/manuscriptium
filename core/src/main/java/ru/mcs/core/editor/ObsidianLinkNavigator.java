package ru.mcs.core.editor;

import java.awt.Desktop;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.SwingWorker;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.vault.VaultNote;
import ru.mcs.core.vault.VaultPaths;

/** Асинхронно разрешает ссылки вне EDT, затем открывает заметку, изображение или attachment. */
public final class ObsidianLinkNavigator implements ObsidianNavigation {

    private final FileEditorManagerImpl editors;
    private final AtomicLong generation = new AtomicLong();
    private volatile Path root;
    private volatile List<Path> notes = List.of();
    private volatile Consumer<String> status = message -> { };

    public ObsidianLinkNavigator(FileEditorManagerImpl editors) {
        this.editors = editors;
    }

    public void setStatusHandler(Consumer<String> status) {
        this.status = java.util.Objects.requireNonNull(status);
    }

    public void setVault(Path root, List<VaultNote> notes) {
        this.root = VaultPaths.normalize(root);
        updateNotes(notes);
        generation.incrementAndGet();
    }

    public void updateNotes(List<VaultNote> notes) {
        this.notes = notes.stream().map(VaultNote::file).map(path -> path.toAbsolutePath().normalize()).toList();
    }

    public void clearVault() {
        root = null;
        notes = List.of();
        generation.incrementAndGet();
    }

    @Override
    public void open(Path source, ObsidianSyntax.Link link, JComponent anchor) {
        Path vaultRoot = root;
        List<Path> vaultNotes = notes;
        long expectedGeneration = generation.get();
        new SwingWorker<Resolution, Void>() {
            @Override
            protected Resolution doInBackground() throws Exception {
                boolean markdown = !link.embed() || link.target().toLowerCase(Locale.ROOT).endsWith(".md")
                        || link.target().isBlank();
                if (markdown) {
                    return ObsidianLinkResolver.note(vaultRoot, source, vaultNotes, link.target())
                            .map(path -> new Resolution(path, link.fragment(), true, null)).orElse(null);
                }
                Path attachment = ObsidianLinkResolver.attachment(vaultRoot, source, link.target()).orElse(null);
                if (attachment == null) {
                    return null;
                }
                javax.swing.Icon preview = isImage(attachment) ? ObsidianImagePreview.load(attachment) : null;
                return new Resolution(attachment, null, false, preview);
            }

            @Override
            protected void done() {
                if (generation.get() != expectedGeneration) {
                    return;
                }
                try {
                    Resolution resolution = get();
                    if (resolution == null) {
                        status.accept(Messages.get("editor.obsidian.unresolved", link.navigationTarget()));
                    } else if (resolution.markdown()) {
                        openNote(resolution.path(), resolution.fragment());
                    } else if (resolution.preview() != null) {
                        ObsidianImagePreview.show(anchor, resolution.path(), resolution.preview());
                    } else {
                        openAttachment(resolution.path());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException | RuntimeException e) {
                    Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                    status.accept(Messages.get("editor.obsidian.open.error", link.navigationTarget(),
                            cause.getMessage() == null ? cause.toString() : cause.getMessage()));
                }
            }
        }.execute();
    }

    private void openNote(Path file, String fragment) {
        if (!editors.openFile(file)) {
            status.accept(Messages.get("editor.obsidian.open.error", file, "no editor provider"));
            return;
        }
        editors.selectedEditor().ifPresent(editor -> {
            if (editor instanceof TextFileEditor text && fragment != null && !fragment.isBlank()) {
                text.selectFragment(fragment);
            }
        });
        status.accept(Messages.get("editor.obsidian.opened", displayPath(file)));
    }

    private void openAttachment(Path file) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                status.accept(Messages.get("editor.obsidian.attachment", displayPath(file)));
                return;
            }
            Desktop.getDesktop().open(file.toFile());
            status.accept(Messages.get("editor.obsidian.opened", displayPath(file)));
        } catch (Exception e) {
            status.accept(Messages.get("editor.obsidian.open.error", displayPath(file), e.toString()));
        }
    }

    private String displayPath(Path file) {
        Path vaultRoot = root;
        Path normalized = file.toAbsolutePath().normalize();
        if (vaultRoot != null && normalized.startsWith(vaultRoot)) {
            return VaultPaths.relativePath(vaultRoot, normalized);
        }
        Path name = normalized.getFileName();
        return name == null ? normalized.toString() : name.toString();
    }

    private static boolean isImage(Path file) {
        Path name = file.getFileName();
        if (name == null) {
            return false;
        }
        String value = name.toString().toLowerCase(Locale.ROOT);
        return value.endsWith(".png") || value.endsWith(".jpg") || value.endsWith(".jpeg")
                || value.endsWith(".gif") || value.endsWith(".bmp") || value.endsWith(".wbmp")
                || value.endsWith(".svg");
    }

    private record Resolution(Path path, String fragment, boolean markdown, javax.swing.Icon preview) {
    }
}
