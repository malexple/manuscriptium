package ru.mcs.markdown.editor.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.markdown.editor.view.MarkdownCanvas;
import ru.mcs.spi.swing.editor.keymap.ShortcutBinding;

class ReformatKeymapTest {

    private static final int CTRL_ALT = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;

    @Test
    void reformatHasAShortcutOnlyInSourceButIsAnActionInBothScopes() throws Exception {
        assertThat(MarkdownKeymapScopes.defaults(MarkdownKeymapScopes.SOURCE))
                .filteredOn(binding -> binding.actionId().equals(EditorActionIds.REFORMAT))
                .extracting(ShortcutBinding::defaultShortcut).containsExactly("shortcut alt L");
        assertThat(MarkdownKeymapScopes.defaults(MarkdownKeymapScopes.CANVAS))
                .filteredOn(binding -> binding.actionId().equals(EditorActionIds.REFORMAT))
                .extracting(ShortcutBinding::defaultShortcut).containsExactly("");

        TestActionManager actions = TestActionManager.editorActions();
        SwingUtilities.invokeAndWait(() -> {
            MarkdownCanvas canvas = new MarkdownCanvas();
            MarkdownCanvas source = new MarkdownCanvas();
            try {
                new TyporaKeymap(actions, InputEvent.CTRL_DOWN_MASK, null, MarkdownKeymapScopes.CANVAS)
                        .install(canvas);
                new TyporaKeymap(actions, InputEvent.CTRL_DOWN_MASK, null, MarkdownKeymapScopes.SOURCE)
                        .install(source);

                KeyStroke ctrlAltL = KeyStroke.getKeyStroke(KeyEvent.VK_L, CTRL_ALT);
                assertThat(canvas.getInputMap(JComponent.WHEN_FOCUSED).get(ctrlAltL))
                        .isEqualTo(EditorActionIds.TABLE_ALIGN_LEFT);
                assertThat(source.getInputMap(JComponent.WHEN_FOCUSED).get(ctrlAltL))
                        .isEqualTo(EditorActionIds.REFORMAT);
                assertThat(canvas.getActionMap().get(EditorActionIds.REFORMAT)).isNotNull();
                assertThat(source.getActionMap().get(EditorActionIds.REFORMAT)).isNotNull();
            } finally {
                canvas.dispose();
                source.dispose();
            }
        });
    }
}
