package ru.mcs.markdown.editor.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.editor.view.CommonKeymap;
import ru.mcs.markdown.editor.view.MarkdownCanvas;
import ru.mcs.spi.swing.editor.keymap.KeymapService;
import ru.mcs.spi.swing.editor.keymap.ShortcutBinding;
import ru.mcs.spi.swing.editor.keymap.ShortcutScope;
import ru.mcs.spi.swing.editor.keymap.Shortcuts;
import ru.mcs.spi.swing.settings.Configurable;

class CommonKeymapTest {

    private static final int MASK = Shortcuts.defaultMask();

    @Test
    void scopeHasDistinctParsableDefaultsAndFallbackNames() {
        ShortcutScope scope = CommonKeymap.scope(key -> key);

        assertThat(scope.id()).isEqualTo(CommonKeymap.SCOPE);
        assertThat(scope.displayName()).isEqualTo("General");
        assertThat(scope.bindings()).hasSize(12);
        Set<KeyStroke> seen = new HashSet<>();
        for (ShortcutBinding binding : scope.bindings()) {
            assertThat(binding.name()).isEqualTo(binding.actionId());
            assertThat(seen.add(Shortcuts.parse(binding.defaultShortcut(), MASK))).as(binding.actionId()).isTrue();
        }
    }

    @Test
    void factoryBindingsStayUntilTheUserChoosesOthers() throws Exception {
        FakeKeymap service = new FakeKeymap();
        CommonKeymap.register(service, key -> key);
        SwingUtilities.invokeAndWait(() -> {
            MarkdownCanvas canvas = new MarkdownCanvas();
            try {
                KeyStroke undo = Shortcuts.parse("shortcut Z", MASK);
                KeyStroke redoAlt = Shortcuts.parse("shortcut Y", MASK);
                KeyStroke altU = Shortcuts.parse("alt U", MASK);

                CommonKeymap.apply(canvas, service);
                assertThat(binding(canvas, undo)).isEqualTo("undo");
                assertThat(binding(canvas, redoAlt)).isEqualTo("redo");

                service.overrides.put(KeymapService.key(CommonKeymap.SCOPE, "undo"), "alt U");
                service.overrides.put(KeymapService.key(CommonKeymap.SCOPE, "redoAlt"), "");
                CommonKeymap.apply(canvas, service);
                assertThat(binding(canvas, undo)).isNull();
                assertThat(binding(canvas, altU)).isEqualTo("editor.common.undo");
                assertThat(canvas.getActionMap().get("editor.common.undo")).isNotNull();
                assertThat(binding(canvas, redoAlt)).isNull();
                assertThat(binding(canvas, Shortcuts.parse("shortcut shift Z", MASK))).isEqualTo("redo");

                service.overrides.clear();
                CommonKeymap.apply(canvas, service);
                assertThat(binding(canvas, undo)).isEqualTo("editor.common.undo");
                assertThat(binding(canvas, altU)).isNull();
                assertThat(binding(canvas, redoAlt)).isEqualTo("editor.common.redoAlt");
            } finally {
                canvas.dispose();
            }
        });
    }

    @Test
    void foreignBindingsOnTheSameKeyAreNotRemoved() throws Exception {
        FakeKeymap service = new FakeKeymap();
        CommonKeymap.register(service, key -> key);
        SwingUtilities.invokeAndWait(() -> {
            MarkdownCanvas canvas = new MarkdownCanvas();
            try {
                KeyStroke redoAlt = Shortcuts.parse("shortcut Y", MASK);
                canvas.getInputMap(JComponent.WHEN_FOCUSED).put(redoAlt, "other.owner");

                service.overrides.put(KeymapService.key(CommonKeymap.SCOPE, "redoAlt"), "");
                CommonKeymap.apply(canvas, service);

                assertThat(binding(canvas, redoAlt)).isEqualTo("other.owner");
            } finally {
                canvas.dispose();
            }
        });
    }

    @Test
    void withoutARegisteredScopeNothingChanges() throws Exception {
        FakeKeymap service = new FakeKeymap();
        SwingUtilities.invokeAndWait(() -> {
            MarkdownCanvas canvas = new MarkdownCanvas();
            try {
                KeyStroke undo = Shortcuts.parse("shortcut Z", MASK);
                CommonKeymap.apply(canvas, service);
                assertThat(binding(canvas, undo)).isEqualTo("undo");
            } finally {
                canvas.dispose();
            }
        });
    }

    private static Object binding(MarkdownCanvas canvas, KeyStroke stroke) {
        return canvas.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
    }

    private static final class FakeKeymap implements KeymapService {

        private final Map<String, String> overrides = new HashMap<>();
        private final List<ShortcutScope> scopes = new ArrayList<>();

        @Override
        public void registerScope(String languageId, ShortcutScope scope) {
            scopes.add(scope);
        }

        @Override
        public void unregisterScopes(String languageId) {
            scopes.clear();
        }

        @Override
        public List<ShortcutScope> scopes(String languageId) {
            return List.copyOf(scopes);
        }

        @Override
        public String shortcut(String scopeId, String actionId) {
            String override = overrides.get(KeymapService.key(scopeId, actionId));
            if (override != null) {
                return override;
            }
            return scopes.stream().filter(scope -> scope.id().equals(scopeId))
                    .flatMap(scope -> scope.bindings().stream())
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
