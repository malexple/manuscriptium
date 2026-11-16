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
import ru.mcs.core.fs.FilePaths;
import ru.mcs.core.i18n.Messages;

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

    public void setProject(Path root, List<Path> notes) {
        this.root = FilePaths.normalize(root);
        updateNotes(notes);
        generation.incrementAndGet();
    }

    public void updateNotes(List<Path> notes) {
        this.notes = notes.stream().map(FilePaths::normalize).toList();
    }

    public void clearProject() {
        root = null;
        notes = List.of();
        generation.incrementAndGet();
    }

    Path projectRoot() {
        return root;
    }

    List<Path> projectNotes() {
        return notes;
    }

    @Override
    public void open(Path source, ObsidianSyntax.Link link, JComponent anchor) {
        Path projectRoot = root;
        List<Path> projectNotes = notes;
        long expectedGeneration = generation.get();
        new SwingWorker<Resolution, Void>() {
            @Override
            protected Resolution doInBackground() throws Exception {
                boolean markdown = !link.embed() || link.target().toLowerCase(Locale.ROOT).endsWith(".md")
                        || link.target().isBlank();
                if (markdown) {
                    return ObsidianLinkResolver.note(projectRoot, source, projectNotes, link.target())
                            .map(path -> new Resolution(path, link.fragment(), true, null)).orElse(null);
                }
                Path attachment = ObsidianLinkResolver.attachment(projectRoot, source, link.target()).orElse(null);
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
        Path projectRoot = root;
        Path normalized = file.toAbsolutePath().normalize();
        if (projectRoot != null && normalized.startsWith(projectRoot)) {
            return FilePaths.relativePath(projectRoot, normalized);
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
