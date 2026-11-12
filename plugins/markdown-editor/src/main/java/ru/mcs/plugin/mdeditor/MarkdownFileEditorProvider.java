package ru.mcs.plugin.mdeditor;

import java.nio.file.Path;
import java.util.Locale;
import ru.mcs.spi.editor.FileEditor;
import ru.mcs.spi.editor.FileEditorProvider;
import ru.mcs.spi.i18n.I18n;

/** Провайдер Markdown-вкладок; чтение содержимого выполняет редактор вне EDT. */
final class MarkdownFileEditorProvider implements FileEditorProvider {

    static final int PRIORITY = 100;

    private final MarkdownEditorState state;
    private final MarkdownEditorRegistry registry;
    private final I18n i18n;

    MarkdownFileEditorProvider(MarkdownEditorState state, MarkdownEditorRegistry registry, I18n i18n) {
        this.state = state;
        this.registry = registry;
        this.i18n = i18n;
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
        return new MarkdownFileEditor(file, state.defaultMode(), registry, i18n);
    }
}
