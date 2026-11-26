package ru.mcs.markdown.editor.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.InputEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.swing.editor.keymap.ShortcutBinding;
import ru.mcs.spi.swing.editor.keymap.ShortcutScope;
import ru.mcs.spi.swing.editor.keymap.Shortcuts;

class MarkdownKeymapScopesTest {

    @Test
    void canvasScopeKeepsEveryEditorActionAndSourceDropsTableCommands() {
        List<ShortcutBinding> canvas = MarkdownKeymapScopes.defaults(MarkdownKeymapScopes.CANVAS);
        List<ShortcutBinding> source = MarkdownKeymapScopes.defaults(MarkdownKeymapScopes.SOURCE);

        assertThat(canvas).extracting(ShortcutBinding::actionId)
                .containsExactlyInAnyOrderElementsOf(EditorActionIds.ALL);
        assertThat(source).extracting(ShortcutBinding::actionId)
                .doesNotContain(EditorActionIds.TABLE_ALIGN_LEFT, EditorActionIds.TABLE_ADD_ROW,
                        EditorActionIds.TABLE_CLEAR_CELL)
                .contains(EditorActionIds.TABLE, EditorActionIds.UNDO, EditorActionIds.BOLD);
        assertThat(source).hasSizeLessThan(canvas.size());
    }

    @Test
    void defaultShortcutsParseAndDoNotCollideInsideAScope() {
        for (String scopeId : List.of(MarkdownKeymapScopes.CANVAS, MarkdownKeymapScopes.SOURCE)) {
            Set<KeyStroke> seen = new HashSet<>();
            for (ShortcutBinding binding : MarkdownKeymapScopes.defaults(scopeId)) {
                if (binding.defaultShortcut().isEmpty()) {
                    continue;
                }
                KeyStroke stroke = Shortcuts.parse(binding.defaultShortcut(), InputEvent.CTRL_DOWN_MASK);
                assertThat(stroke).as("%s %s", scopeId, binding.actionId()).isNotNull();
                assertThat(seen.add(stroke)).as("duplicate %s in %s", binding.defaultShortcut(), scopeId).isTrue();
            }
        }
    }

    @Test
    void scopesUseLocalizedTitlesAndFallBackToIdsForMissingNames() {
        ShortcutScope scope = MarkdownKeymapScopes.scope(MarkdownKeymapScopes.SOURCE,
                key -> key.equals("action.bold") ? "Bold!" : key);

        assertThat(scope.displayName()).isEqualTo("keys.scope.source");
        assertThat(scope.bindings()).filteredOn(binding -> binding.actionId().equals(EditorActionIds.BOLD))
                .extracting(ShortcutBinding::name).containsExactly("Bold!");
        assertThat(scope.bindings()).filteredOn(binding -> binding.actionId().equals(EditorActionIds.ITALIC))
                .extracting(ShortcutBinding::name).containsExactly("italic");
    }
}
