package ru.mcs.markdown.properties;

import java.nio.file.Path;
import javax.swing.JComponent;
import ru.mcs.spi.swing.PluginContext;
import ru.mcs.spi.editor.EditorTopics;
import ru.mcs.spi.editor.FileEditorListener;

/** Front matter активной заметки как виджеты, без подмены source-редактора. */
public final class PropertiesModule {

    private PluginContext ctx;
    private PropertiesPanel panel;

    public void start(PluginContext context) {
        this.ctx = context;
        ctx.messageBus().subscribeOnEdt(EditorTopics.EDITOR, new FileEditorListener() {
                    public void fileClosed(Path file) {
                updatePanel();
            }

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
