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
import ru.mcs.markdown.api.MarkdownParser;
import ru.mcs.markdown.api.ObsidianLinkService;
import ru.mcs.markdown.api.EditorStyleService;
import ru.mcs.spi.service.AtomicFileStore;
import ru.mcs.spi.service.Scope;
import ru.mcs.spi.swing.settings.Configurable;

/** Жизненный цикл встроенного Canvas-редактора и его сервисов. */
public final class MarkdownEditorModule {

    static final String TOGGLE_MODE = EditorActionIds.TOGGLE_MODE;

    private final MarkdownEditorState state = new MarkdownEditorState();
    private final MarkdownEditorRegistry registry = new MarkdownEditorRegistry();
    private final MarkdownSourceViewState sourceView = new MarkdownSourceViewState();
    private final MarkdownCodeStyleState codeStyle = new MarkdownCodeStyleState();
    private PluginContext ctx;
    private AtomicFileStore fileStore;
    private MarkdownParser parser;
    private EditorStyleService styles;
    private ExecutorService parserExecutor;
    private ExecutorService widgetExecutor;
    private WidgetRegistry widgets;
    private EditorActionFactory actionFactory;
    private ru.mcs.spi.swing.editor.style.ColorSchemeService colorScheme;
    private ru.mcs.editor.view.TokenColors tokenColors = new ru.mcs.markdown.render.MarkdownTokenColors();
    private Runnable colorSchemeListener;
    private ru.mcs.spi.swing.editor.keymap.KeymapService keymap;
    private Runnable keymapListener;

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
        ctx.state().register("sourceView", Scope.APPLICATION, sourceView);
        ctx.state().register("codeStyle", Scope.APPLICATION, codeStyle);
        colorScheme = ctx.services().find(ru.mcs.spi.swing.editor.style.ColorSchemeService.class).orElse(null);
        if (colorScheme != null) {
            colorScheme.registerGroup(ru.mcs.markdown.render.MarkdownColorScheme.group(key -> ctx.i18n().get(key)));
            tokenColors = ru.mcs.markdown.render.MarkdownColorScheme.colors(colorScheme);
            colorSchemeListener = () -> javax.swing.SwingUtilities.invokeLater(this::refreshSourceViews);
            colorScheme.addChangeListener(colorSchemeListener);
        }
        keymap = ctx.services().find(ru.mcs.spi.swing.editor.keymap.KeymapService.class).orElse(null);
        if (keymap != null) {
            ru.mcs.markdown.editor.action.MarkdownKeymapScopes.register(keymap, key -> ctx.i18n().get(key));
            ru.mcs.editor.view.CommonKeymap.register(keymap, key -> ctx.i18n().get(key));
            keymapListener = () -> javax.swing.SwingUtilities.invokeLater(this::refreshKeymaps);
            keymap.addChangeListener(keymapListener);
        }
        actionFactory = new EditorActionFactory(ctx.i18n(), this::selectedEditor);
        actionFactory.useCodeStyle(codeStyle::style);
    }

    public void stop() {
        if (keymap != null) {
            keymap.removeChangeListener(keymapListener);
            keymap.unregisterScopes(ru.mcs.markdown.editor.action.MarkdownKeymapScopes.LANGUAGE_ID);
        }
        if (colorScheme != null) {
            colorScheme.removeChangeListener(colorSchemeListener);
            colorScheme.unregisterGroup(ru.mcs.markdown.render.MarkdownColorScheme.GROUP_ID);
        }
        if (parserExecutor != null) {
            parserExecutor.shutdownNow();
        }
        if (widgetExecutor != null) {
            widgetExecutor.shutdownNow();
        }
    }

    /** Страница Editor → Code Style → Markdown. */
    ru.mcs.spi.swing.settings.Configurable createCodeStyleConfigurable() {
        return new MarkdownCodeStyleConfigurable(codeStyle, key -> ctx.i18n().get(key));
    }

    /** Страница Editor → General → Smart Keys → Markdown; null, если сервис сочетаний недоступен. */
    ru.mcs.spi.swing.settings.Configurable createSmartKeysConfigurable() {
        return keymap == null ? null
                : keymap.createSettings(ru.mcs.markdown.editor.action.MarkdownKeymapScopes.LANGUAGE_ID);
    }

    private void refreshKeymaps() {
        registry.forEach(editor -> editor.useKeymap(keymap));
    }

    private void refreshSourceViews() {
        registry.forEach(editor -> editor.setSyntaxHighlighting(sourceView.showSyntaxHighlighting(), tokenColors));
    }

    public AnAction createAction(String id) {
        return actionFactory == null ? null : actionFactory.create(id);
    }

    public FileEditorProvider createFileEditorProvider() {
        MarkdownFileEditorProvider provider = new MarkdownFileEditorProvider(state, registry, ctx.i18n(), fileStore,
                parser, parserExecutor, styles, ctx.actions(), widgets);
        provider.useSourceView(sourceView);
        provider.useColorScheme(tokenColors);
        provider.useKeymap(keymap);
        return provider;
    }

    public Configurable createConfigurable() {
        return new MarkdownEditorConfigurable(state, ctx.i18n());
    }

    public Configurable createGeneralConfigurable() {
        return new MarkdownGeneralConfigurable(sourceView,
                current -> registry.forEach(editor -> {
                    editor.setLineNumbersVisible(current.showLineNumbers());
                    editor.setSyntaxHighlighting(current.showSyntaxHighlighting(), tokenColors);
                }),
                key -> ctx.i18n().get(key));
    }

    private Optional<MarkdownFileEditor> selectedEditor() {
        return registry.find(ctx.editors().getSelectedFile().orElse(null));
    }
}
