package ru.mcs.plugin.mdeditor;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import ru.mcs.plugin.mdeditor.widget.WidgetRegistry;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.editor.FileEditor;
import ru.mcs.spi.editor.FileEditorProvider;
import ru.mcs.spi.editor.MarkdownParser;
import ru.mcs.spi.editor.style.EditorStyleService;
import ru.mcs.spi.i18n.I18n;
import ru.mcs.spi.service.AtomicFileStore;

/** Провайдер Markdown-вкладок; чтение содержимого выполняет редактор вне EDT. */
final class MarkdownFileEditorProvider implements FileEditorProvider {

    static final int PRIORITY = 100;

    private final MarkdownEditorState state;
    private final MarkdownEditorRegistry registry;
    private final I18n i18n;
    private final AtomicFileStore fileStore;
    private final MarkdownParser parser;
    private final Executor parserExecutor;
    private final EditorStyleService styles;
    private final ActionManager actions;
    private final WidgetRegistry widgets;

    MarkdownFileEditorProvider(MarkdownEditorState state, MarkdownEditorRegistry registry, I18n i18n,
                               AtomicFileStore fileStore, MarkdownParser parser, Executor parserExecutor) {
        this(state, registry, i18n, fileStore, parser, parserExecutor, null, null);
    }

    MarkdownFileEditorProvider(MarkdownEditorState state, MarkdownEditorRegistry registry, I18n i18n,
                               AtomicFileStore fileStore, MarkdownParser parser, Executor parserExecutor,
                               EditorStyleService styles) {
        this(state, registry, i18n, fileStore, parser, parserExecutor, styles, null);
    }

    MarkdownFileEditorProvider(MarkdownEditorState state, MarkdownEditorRegistry registry, I18n i18n,
                               AtomicFileStore fileStore, MarkdownParser parser, Executor parserExecutor,
                               EditorStyleService styles, ActionManager actions) {
        this(state, registry, i18n, fileStore, parser, parserExecutor, styles, actions, null);
    }

    MarkdownFileEditorProvider(MarkdownEditorState state, MarkdownEditorRegistry registry, I18n i18n,
                               AtomicFileStore fileStore, MarkdownParser parser, Executor parserExecutor,
                               EditorStyleService styles, ActionManager actions, WidgetRegistry widgets) {
        this.state = state;
        this.registry = registry;
        this.i18n = i18n;
        this.fileStore = Objects.requireNonNull(fileStore, "fileStore");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.parserExecutor = Objects.requireNonNull(parserExecutor, "parserExecutor");
        this.styles = styles;
        this.actions = actions;
        this.widgets = widgets;
    }

    @Override
    public String getId() {
        return "markdown.editor";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean accept(Path file) {
        Path name = file == null ? null : file.getFileName();
        return name != null && name.toString().toLowerCase(Locale.ROOT).endsWith(".md");
    }

    @Override
    public FileEditor createEditor(Path file) {
        return new MarkdownFileEditor(file, state.defaultMode(), registry, i18n, fileStore, parser, parserExecutor,
                styles, actions, widgets);
    }
}
