package ru.mcs.markdown.editor.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.markdown.editor.view.MarkdownCanvas;
import ru.mcs.spi.swing.editor.keymap.KeymapService;
import ru.mcs.spi.swing.editor.keymap.ShortcutBinding;
import ru.mcs.spi.swing.editor.keymap.ShortcutScope;
import ru.mcs.spi.swing.settings.Configurable;

class TyporaKeymapScopesTest {

    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;

    @Test
    void ctrlAltLAlignsTablesInCanvasButIsFreeInSource() throws Exception {
        TestActionManager actions = TestActionManager.editorActions();
        SwingUtilities.invokeAndWait(() -> {
            MarkdownCanvas canvas = new MarkdownCanvas();
            MarkdownCanvas source = new MarkdownCanvas();
            try {
                new TyporaKeymap(actions, CTRL, null, MarkdownKeymapScopes.CANVAS).install(canvas);
                new TyporaKeymap(actions, CTRL, null, MarkdownKeymapScopes.SOURCE).install(source);

                KeyStroke ctrlAltL = KeyStroke.getKeyStroke(KeyEvent.VK_L, CTRL | InputEvent.ALT_DOWN_MASK);
                assertThat(binding(canvas, ctrlAltL)).isEqualTo(EditorActionIds.TABLE_ALIGN_LEFT);
                assertThat(binding(source, ctrlAltL)).isNull();
                assertThat(binding(source, KeyStroke.getKeyStroke(KeyEvent.VK_Z, CTRL)))
                        .isEqualTo(EditorActionIds.UNDO);
            } finally {
                canvas.dispose();
                source.dispose();
            }
        });
    }

    @Test
    void userShortcutsReplaceDefaultsAndUninstallRemovesThem() throws Exception {
        TestActionManager actions = TestActionManager.editorActions();
        FakeKeymap service = new FakeKeymap();
        service.overrides.put(KeymapService.key(MarkdownKeymapScopes.SOURCE, EditorActionIds.BOLD), "alt B");
        service.overrides.put(KeymapService.key(MarkdownKeymapScopes.SOURCE, EditorActionIds.ITALIC), "");
        SwingUtilities.invokeAndWait(() -> {
            MarkdownCanvas source = new MarkdownCanvas();
            try {
                TyporaKeymap keymap = new TyporaKeymap(actions, CTRL, service, MarkdownKeymapScopes.SOURCE);
                keymap.install(source);

                assertThat(binding(source, KeyStroke.getKeyStroke(KeyEvent.VK_B, CTRL))).isNull();
                assertThat(binding(source, KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.ALT_DOWN_MASK)))
                        .isEqualTo(EditorActionIds.BOLD);
                assertThat(binding(source, KeyStroke.getKeyStroke(KeyEvent.VK_I, CTRL))).isNull();
                assertThat(source.getActionMap().get(EditorActionIds.ITALIC)).isNull();

                keymap.uninstall();
                assertThat(binding(source, KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.ALT_DOWN_MASK))).isNull();
                assertThat(source.getActionMap().get(EditorActionIds.BOLD)).isNull();
            } finally {
                source.dispose();
            }
        });
    }

    private static Object binding(MarkdownCanvas canvas, KeyStroke stroke) {
        return canvas.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
    }

    private static final class FakeKeymap implements KeymapService {

        private final Map<String, String> overrides = new HashMap<>();

        @Override
        public void registerScope(String languageId, ShortcutScope scope) {
        }

        @Override
        public void unregisterScopes(String languageId) {
        }

        @Override
        public List<ShortcutScope> scopes(String languageId) {
            return List.of();
        }

        @Override
        public String shortcut(String scopeId, String actionId) {
            String override = overrides.get(KeymapService.key(scopeId, actionId));
            if (override != null) {
                return override;
            }
            return MarkdownKeymapScopes.defaults(scopeId).stream()
                    .filter(binding -> binding.actionId().equals(actionId))
                    .map(ShortcutBinding::defaultShortcut).findFirst().orElse("");
        }

        @Override
        public Map<String, String> overrides() {
            return overrides;
        }

        @Override
        public void setOverrides(Map<String, String> next) {
        }

        @Override
        public Configurable createSettings(String languageId) {
            return null;
        }

        @Override
        public void addChangeListener(Runnable listener) {
        }

        @Override
        public void removeChangeListener(Runnable listener) {
        }
    }
}
