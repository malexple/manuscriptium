package ru.mcs.plugin.mdeditor;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JComponent;
import ru.mcs.plugin.mdeditor.mode.EditorMode;
import ru.mcs.spi.Plugin;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.editor.FileEditorProvider;
import ru.mcs.spi.editor.MarkdownParser;
import ru.mcs.spi.editor.style.EditorStyleService;
import ru.mcs.spi.service.AtomicFileStore;
import ru.mcs.spi.service.Scope;
import ru.mcs.spi.settings.Configurable;

/** Каркас bundled Canvas-редактора. Все вклады объявлены в plugin.yml и создаются по запросу хоста. */
public final class MarkdownEditorPlugin implements Plugin {

    static final String TOGGLE_MODE = "markdown.editor.toggleMode";

    private final MarkdownEditorState state = new MarkdownEditorState();
    private final MarkdownEditorRegistry registry = new MarkdownEditorRegistry();
    private PluginContext ctx;
    private AtomicFileStore fileStore;
    private MarkdownParser parser;
    private EditorStyleService styles;
    private ExecutorService parserExecutor;

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
        ctx.state().register("editor", Scope.APPLICATION, state);
    }

    @Override
    public void stop() {
        if (parserExecutor != null) {
            parserExecutor.shutdownNow();
        }
    }

    @Override
    public AnAction createAction(String id) {
        if (!TOGGLE_MODE.equals(id)) {
            return null;
        }
        return new AnAction(ctx.i18n().get("action.toggleMode")) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                selectedEditor().ifPresent(editor -> editor.setMode(editor.mode() == EditorMode.SOURCE
                        ? EditorMode.WYSIWYG : EditorMode.SOURCE));
            }

            @Override
            public void update(AnActionEvent e) {
                Optional<MarkdownFileEditor> selected = selectedEditor();
                e.getPresentation().setEnabledAndVisible(selected.isPresent());
                selected.ifPresent(editor -> e.getPresentation().setText(ctx.i18n().get(
                        editor.mode() == EditorMode.SOURCE ? "action.toCanvas" : "action.toSource")));
            }
        };
    }

    @Override
    public FileEditorProvider createFileEditorProvider(String id) {
        return "markdown.editor".equals(id)
                ? new MarkdownFileEditorProvider(state, registry, ctx.i18n(), fileStore, parser, parserExecutor)
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
