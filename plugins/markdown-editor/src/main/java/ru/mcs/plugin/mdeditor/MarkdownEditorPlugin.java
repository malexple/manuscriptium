package ru.mcs.plugin.mdeditor;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JComponent;
import ru.mcs.plugin.mdeditor.action.EditorActionIds;
import ru.mcs.plugin.mdeditor.widget.WidgetRegistry;
import ru.mcs.spi.Plugin;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.editor.BlockRendererExtensionRegistry;
import ru.mcs.spi.editor.FileEditorProvider;
import ru.mcs.spi.editor.MathRenderingService;
import ru.mcs.spi.editor.MarkdownParser;
import ru.mcs.spi.editor.ObsidianLinkService;
import ru.mcs.spi.editor.style.EditorStyleService;
import ru.mcs.spi.service.AtomicFileStore;
import ru.mcs.spi.service.Scope;
import ru.mcs.spi.settings.Configurable;

/** Каркас bundled Canvas-редактора. Все вклады объявлены в plugin.yml и создаются по запросу хоста. */
public final class MarkdownEditorPlugin implements Plugin {

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

    @Override
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

    @Override
    public void stop() {
        if (parserExecutor != null) {
            parserExecutor.shutdownNow();
        }
        if (widgetExecutor != null) {
            widgetExecutor.shutdownNow();
        }
    }

    @Override
    public AnAction createAction(String id) {
        return actionFactory == null ? null : actionFactory.create(id);
    }

    @Override
    public FileEditorProvider createFileEditorProvider(String id) {
        return "markdown.editor".equals(id)
                ? new MarkdownFileEditorProvider(state, registry, ctx.i18n(), fileStore, parser, parserExecutor, styles,
                        ctx.actions(), widgets)
                : null;
    }

    @Override
    public Configurable createConfigurable(String id) {
        return "markdown.editor.settings".equals(id) ? new MarkdownEditorConfigurable(state, ctx.i18n()) : null;
    }

    private Optional<MarkdownFileEditor> selectedEditor() {
        return registry.find(ctx.editors().getSelectedFile().orElse(null));
    }
}
