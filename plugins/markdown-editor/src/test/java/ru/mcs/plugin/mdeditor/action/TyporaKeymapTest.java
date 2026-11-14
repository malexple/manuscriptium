package ru.mcs.plugin.mdeditor.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
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
    void intellijShortcutsOwnPlainKeysWithoutLegacyConflicts() throws Exception {
        TestActionManager actions = TestActionManager.editorActions();
        SwingUtilities.invokeAndWait(() -> {
            EditorCanvas canvas = new EditorCanvas();
            try {
                new TyporaKeymap(actions, InputEvent.CTRL_DOWN_MASK).install(canvas);

                assertThat(binding(canvas, KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK))
                        .isEqualTo(EditorActionIds.TOGGLE_CASE);
                assertThat(binding(canvas, KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK))
                        .isEqualTo(EditorActionIds.DUPLICATE_LINE);
                assertThat(binding(canvas, KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK))
                        .isEqualTo(EditorActionIds.DELETE_LINE);
                assertThat(binding(canvas, KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK))
                        .isEqualTo(EditorActionIds.EXTEND_SELECTION);
                assertThat(binding(canvas, KeyEvent.VK_U,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))
                        .isEqualTo(EditorActionIds.UNDERLINE);
                assertThat(binding(canvas, KeyEvent.VK_Z,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))
                        .isEqualTo(EditorActionIds.REDO);
            } finally {
                canvas.dispose();
            }
        });
    }

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

    private static Object binding(EditorCanvas canvas, int keyCode, int modifiers) {
        return canvas.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(keyCode, modifiers));
    }
}
