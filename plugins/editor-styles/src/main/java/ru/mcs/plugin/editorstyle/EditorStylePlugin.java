package ru.mcs.plugin.editorstyle;

import ru.mcs.spi.Plugin;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.editor.style.EditorStyleService;
import ru.mcs.spi.editor.style.ShellThemeService;
import ru.mcs.spi.service.Scope;

/** Публикует CSS-движок через JDK-only SPI и скрывает все реализации в plugin layer. */
public final class EditorStylePlugin implements Plugin {

    private DefaultEditorStyleService service;

    @Override
    public void start(PluginContext context) {
        EditorStyleState state = new EditorStyleState();
        context.state().register("style", Scope.APPLICATION, state);
        service = new DefaultEditorStyleService(state, context.services().get(ShellThemeService.class), context::log);
        context.services().register(EditorStyleService.class, service);
        service.reloadAsync();
        context.log("Typora CSS engine registered; themes load in background");
    }

    @Override
    public void stop() {
        if (service != null) {
            service.close();
        }
    }
}
