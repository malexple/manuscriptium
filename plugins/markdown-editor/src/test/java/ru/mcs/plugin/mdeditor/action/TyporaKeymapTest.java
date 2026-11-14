package ru.mcs.plugin.mdeditor.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.plugin.mdeditor.model.DocumentModelImpl;
import ru.mcs.plugin.mdeditor.model.DocumentProperties;
import ru.mcs.plugin.mdeditor.model.LineEnding;
import ru.mcs.plugin.mdeditor.view.EditorCanvas;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;

class TyporaKeymapTest {

    @Test
    void everyEditorCommandHasKeymapActionAndDelegatesToRegisteredAction() throws Exception {
        TestActionManager actions = TestActionManager.editorActions();
        AtomicInteger boldCalls = new AtomicInteger();
        actions.unregisterAction(EditorActionIds.BOLD);
        actions.registerAction(EditorActionIds.BOLD, new AnAction("Bold") {
            @Override
            public void actionPerformed(AnActionEvent event) {
                boldCalls.incrementAndGet();
            }
        });
        SwingUtilities.invokeAndWait(() -> {
            EditorCanvas canvas = new EditorCanvas();
            try {
                canvas.setDocument(new DocumentModelImpl("body",
                        new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false)));
                new TyporaKeymap(actions).install(canvas);

                for (String id : EditorActionIds.ALL) {
                    assertThat(canvas.getActionMap().get(id)).as("keymap action %s", id).isNotNull();
                }
                canvas.getActionMap().get(EditorActionIds.BOLD).actionPerformed(null);
                assertThat(boldCalls).hasValue(1);
            } finally {
                canvas.dispose();
            }
        });
    }
}
