package ru.mcs.markdown.editor;

import java.awt.Desktop;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.SwingWorker;
import ru.mcs.spi.fs.FilePaths;
import ru.mcs.spi.project.ProjectAccess;
import ru.mcs.spi.project.ViewDefinition;
import ru.mcs.spi.swing.editor.FileEditorManager;
import ru.mcs.spi.swing.editor.FragmentNavigableEditor;

/** Асинхронно разрешает ссылки вне EDT, затем открывает заметку, изображение или attachment. */
public final class ObsidianLinkNavigator implements ObsidianNavigation {

    private final FileEditorManager editors;
    private final ProjectAccess projects;
    private volatile Consumer<String> status = message -> { };

    public ObsidianLinkNavigator(FileEditorManager editors, ProjectAccess projects) {
        this.editors = java.util.Objects.requireNonNull(editors);
        this.projects = java.util.Objects.requireNonNull(projects);
    }

    /** Локальный Project используется только изолированными тестами и простыми встраиваниями редактора. */
    ObsidianLinkNavigator(FileEditorManager editors) {
        this(editors, new LocalProjectAccess());
    }

    public void setStatusHandler(Consumer<String> status) {
        this.status = java.util.Objects.requireNonNull(status);
    }

    void setProject(Path root, List<Path> notes) {
        if (projects instanceof LocalProjectAccess local) {
            local.set(root, notes);
        }
    }

    Path projectRoot() {
        return projects.root().orElse(null);
    }

    List<Path> projectNotes() {
        return projects.files().stream().filter(ObsidianLinkNavigator::isMarkdown).toList();
    }

    @Override
    public void open(Path source, ObsidianSyntax.Link link, JComponent anchor) {
        Path projectRoot = projectRoot();
        List<Path> projectNotes = projectNotes();
        long expectedRevision = projects.revision();
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
                if (projects.revision() != expectedRevision) {
                    return;
                }
                try {
                    Resolution resolution = get();
                    if (resolution == null) {
                        status.accept(MarkdownMessages.get("editor.obsidian.unresolved", link.navigationTarget()));
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
                    status.accept(MarkdownMessages.get("editor.obsidian.open.error", link.navigationTarget(),
                            cause.getMessage() == null ? cause.toString() : cause.getMessage()));
                }
            }
        }.execute();
    }

    private void openNote(Path file, String fragment) {
        if (!editors.openFile(file)) {
            status.accept(MarkdownMessages.get("editor.obsidian.open.error", file, "no editor provider"));
            return;
        }
        if (fragment != null && !fragment.isBlank()) {
            editors.selectedEditor().ifPresent(editor -> {
                if (editor instanceof FragmentNavigableEditor navigable) {
                    navigable.selectFragment(fragment);
                }
            });
        }
        status.accept(MarkdownMessages.get("editor.obsidian.opened", displayPath(file)));
    }

    private void openAttachment(Path file) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                status.accept(MarkdownMessages.get("editor.obsidian.attachment", displayPath(file)));
                return;
            }
            Desktop.getDesktop().open(file.toFile());
            status.accept(MarkdownMessages.get("editor.obsidian.opened", displayPath(file)));
        } catch (Exception e) {
            status.accept(MarkdownMessages.get("editor.obsidian.open.error", displayPath(file), e.toString()));
        }
    }

    private String displayPath(Path file) {
        Path projectRoot = projectRoot();
        Path normalized = file.toAbsolutePath().normalize();
        if (projectRoot != null && normalized.startsWith(projectRoot)) {
            return FilePaths.relativePath(projectRoot, normalized);
        }
        Path name = normalized.getFileName();
        return name == null ? normalized.toString() : name.toString();
    }

    private static boolean isMarkdown(Path file) {
        Path name = file.getFileName();
        return name != null && name.toString().toLowerCase(Locale.ROOT).endsWith(".md");
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

    private static final class LocalProjectAccess implements ProjectAccess {
        private Path root;
        private List<Path> files = List.of();
        private long revision;

        private void set(Path newRoot, List<Path> newFiles) {
            root = FilePaths.normalize(newRoot);
            files = newFiles.stream().map(FilePaths::normalize).toList();
            revision++;
        }

        @Override
        public Optional<Path> root() {
            return Optional.ofNullable(root);
        }

        @Override
        public List<Path> files() {
            return files;
        }

        @Override
        public Optional<ViewDefinition> view(String id) {
            return Optional.empty();
        }

        @Override
        public long revision() {
            return revision;
        }
    }
}
