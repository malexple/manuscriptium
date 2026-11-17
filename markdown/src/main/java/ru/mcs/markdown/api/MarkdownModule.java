package ru.mcs.markdown.api;

import ru.mcs.markdown.editor.MarkdownContributions;
import ru.mcs.markdown.editor.MarkdownEditorModule;
import ru.mcs.markdown.math.JLaTeXMathRenderingService;
import ru.mcs.markdown.notes.NotesModule;
import ru.mcs.markdown.parser.FlexmarkMarkdownParser;
import ru.mcs.markdown.properties.PropertiesModule;
import ru.mcs.markdown.style.DefaultEditorStyleService;
import ru.mcs.markdown.style.EditorStyleState;
import ru.mcs.spi.editor.MarkdownParser;
import ru.mcs.spi.editor.style.ShellThemeService;
import ru.mcs.spi.service.Scope;
import ru.mcs.spi.swing.BuiltInModule;
import ru.mcs.spi.swing.PluginContext;
import ru.mcs.spi.swing.editor.MathRenderingService;
import ru.mcs.spi.swing.editor.style.EditorStyleService;

/** Единственная публичная точка компоновки встроенной Markdown-функциональности. */
public final class MarkdownModule implements BuiltInModule {

    private MarkdownEditorModule editor;
    private DefaultEditorStyleService styles;

    @Override
    public String id() {
        return "markdown";
    }

    @Override
    public String i18nBaseName() {
        return "ru/mcs/markdown/messages/messages";
    }

    @Override
    public void start(PluginContext context) {
        context.services().register(MarkdownParser.class, new FlexmarkMarkdownParser());

        EditorStyleState styleState = new EditorStyleState();
        context.state().register("style", Scope.APPLICATION, styleState);
        styles = new DefaultEditorStyleService(styleState, context.services().get(ShellThemeService.class), context::log);
        context.services().register(EditorStyleService.class, styles);
        styles.reloadAsync();

        context.services().register(MathRenderingService.class, new JLaTeXMathRenderingService());

        editor = new MarkdownEditorModule();
        editor.start(context);
        PropertiesModule properties = new PropertiesModule();
        properties.start(context);
        NotesModule notes = new NotesModule();
        notes.start(context);
        MarkdownContributions.register(context, editor, properties, notes);
        context.log("Built-in Markdown parser, editor, styles, math, properties and notes registered");
    }

    @Override
    public void stop() {
        if (editor != null) {
            editor.stop();
            editor = null;
        }
        if (styles != null) {
            styles.close();
            styles = null;
        }
    }
}
