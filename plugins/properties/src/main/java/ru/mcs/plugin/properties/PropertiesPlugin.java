package ru.mcs.plugin.properties;

import java.nio.file.Path;
import javax.swing.JComponent;
import ru.mcs.spi.Plugin;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.editor.EditorTopics;
import ru.mcs.spi.editor.FileEditorListener;

/** Front matter активной заметки как виджеты, без подмены source-редактора. */
public final class PropertiesPlugin implements Plugin {

    private PluginContext ctx;
    private PropertiesPanel panel;

    @Override
    public void start(PluginContext context) {
        this.ctx = context;
        ctx.messageBus().subscribeOnEdt(EditorTopics.EDITOR, new FileEditorListener() {
            @Override
            public void fileClosed(Path file) {
                updatePanel();
            }

            @Override
            public void activeFileChanged(Path file) {
                updatePanel();
            }

            private void updatePanel() {
                if (panel != null) {
                    panel.showActive(ctx.editors().getSelectedFile().orElse(null));
                }
            }
        });
    }

    @Override
    public JComponent createToolWindowContent(String id) {
        if (!"properties.panel".equals(id)) {
            return null;
        }
        if (panel == null) {
            panel = new PropertiesPanel(ctx);
        }
        return panel;
    }
}
