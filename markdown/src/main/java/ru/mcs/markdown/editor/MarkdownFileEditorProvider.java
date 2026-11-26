package ru.mcs.markdown.editor;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import ru.mcs.markdown.widget.WidgetRegistry;
import ru.mcs.spi.swing.action.ActionManager;
import ru.mcs.spi.swing.editor.FileEditor;
import ru.mcs.spi.swing.editor.FileEditorProvider;
import ru.mcs.markdown.api.MarkdownParser;
import ru.mcs.markdown.api.EditorStyleService;
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
    private MarkdownSourceViewState sourceView;

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

    /** Настройки вкладки «Исходник» для новых редакторов (номера строк). */
    void useSourceView(MarkdownSourceViewState sourceView) {
        this.sourceView = sourceView;
    }

    private ru.mcs.editor.view.TokenColors tokenColors = new ru.mcs.markdown.render.MarkdownTokenColors();

    /** Цвета подсветки вкладки «Исходник» из общей цветовой схемы. */
    void useColorScheme(ru.mcs.editor.view.TokenColors tokenColors) {
        this.tokenColors = tokenColors;
    }

    private ru.mcs.spi.swing.editor.keymap.KeymapService keymap;

    /** Настраиваемые сочетания клавиш областей Canvas и «Исходник». */
    void useKeymap(ru.mcs.spi.swing.editor.keymap.KeymapService keymap) {
        this.keymap = keymap;
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
        MarkdownFileEditor editor = new MarkdownFileEditor(file, state.defaultMode(), registry, i18n, fileStore,
                parser, parserExecutor, styles, actions, widgets);
        if (sourceView != null) {
            editor.setLineNumbersVisible(sourceView.showLineNumbers());
            editor.setSyntaxHighlighting(sourceView.showSyntaxHighlighting(), tokenColors);
        }
        if (keymap != null) {
            editor.useKeymap(keymap);
        }
        return editor;
    }
}
