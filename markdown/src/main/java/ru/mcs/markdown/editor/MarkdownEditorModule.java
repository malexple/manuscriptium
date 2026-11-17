package ru.mcs.markdown.editor;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ru.mcs.markdown.editor.action.EditorActionIds;
import ru.mcs.markdown.widget.WidgetRegistry;
import ru.mcs.spi.swing.PluginContext;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.editor.BlockRendererExtensionRegistry;
import ru.mcs.spi.swing.editor.FileEditorProvider;
import ru.mcs.spi.swing.editor.MathRenderingService;
import ru.mcs.spi.editor.MarkdownParser;
import ru.mcs.spi.swing.editor.ObsidianLinkService;
import ru.mcs.spi.swing.editor.style.EditorStyleService;
import ru.mcs.spi.service.AtomicFileStore;
import ru.mcs.spi.service.Scope;
import ru.mcs.spi.swing.settings.Configurable;

/** Жизненный цикл встроенного Canvas-редактора и его сервисов. */
public final class MarkdownEditorModule {

    static final String TOGGLE_MODE = EditorActionIds.TOGGLE_MODE;

    private final MarkdownEditorState state = new MarkdownEditorState();
    private final MarkdownEditorRegistry registry = new MarkdownEditorRegistry();
    private PluginContext ctx;
    private AtomicFileStore fileStore;
    private MarkdownParser parser;
    private EditorStyleService styles;
    private ExecutorService parserExecutor;
    private ExecutorService widgetExecutor;
    private WidgetRegistry widgets;
    private EditorActionFactory actionFactory;

    public void start(PluginContext context) {
        ctx = context;
        fileStore = context.services().get(AtomicFileStore.class);
        parser = context.services().get(MarkdownParser.class);
        styles = context.services().get(EditorStyleService.class);
        parserExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "manuscriptium-markdown-parser");
            thread.setDaemon(true);
            return thread;
        });
        widgetExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "manuscriptium-widget-loader");
            thread.setDaemon(true);
            return thread;
        });
        widgets = new WidgetRegistry(widgetExecutor, context.services().find(MathRenderingService.class).orElse(null),
                context.services().find(ObsidianLinkService.class).orElse(null), fileStore, context.i18n());
        context.services().register(WidgetRegistry.class, widgets);
        context.services().register(BlockRendererExtensionRegistry.class, widgets);
        ctx.state().register("editor", Scope.APPLICATION, state);
        actionFactory = new EditorActionFactory(ctx.i18n(), this::selectedEditor);
    }

    public void stop() {
        if (parserExecutor != null) {
            parserExecutor.shutdownNow();
        }
        if (widgetExecutor != null) {
            widgetExecutor.shutdownNow();
        }
    }

    public AnAction createAction(String id) {
        return actionFactory == null ? null : actionFactory.create(id);
    }

    public FileEditorProvider createFileEditorProvider() {
        return new MarkdownFileEditorProvider(state, registry, ctx.i18n(), fileStore, parser, parserExecutor,
                styles, ctx.actions(), widgets);
    }

    public Configurable createConfigurable() {
        return new MarkdownEditorConfigurable(state, ctx.i18n());
    }

    private Optional<MarkdownFileEditor> selectedEditor() {
        return registry.find(ctx.editors().getSelectedFile().orElse(null));
    }
}
