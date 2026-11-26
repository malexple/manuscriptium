package ru.mcs.core.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.swing.editor.keymap.ShortcutBinding;
import ru.mcs.spi.swing.editor.keymap.ShortcutScope;

class KeymapSharedConflictTest {

    private static KeymapServiceImpl service() {
        KeymapServiceImpl service = new KeymapServiceImpl();
        service.registerScope("editor", new ShortcutScope("common", "General",
                List.of(new ShortcutBinding("undo", "Undo", "shortcut Z"))));
        service.registerScope("lang", new ShortcutScope("s", "Scope", List.of(
                new ShortcutBinding("a", "Action A", "shortcut L"),
                new ShortcutBinding("t", "Toggle case", "shortcut U"))));
        service.registerScope("other", new ShortcutScope("o", "Other scope",
                List.of(new ShortcutBinding("x", "Action X", "shortcut Z"))));
        return service;
    }

    @Test
    void commonKeysOverlapEveryLanguageAndLanguagesOverlapOnlyCommon() {
        KeymapServiceImpl service = service();
        KeymapConfigurable common = new KeymapConfigurable(service, "editor");
        KeymapConfigurable lang = new KeymapConfigurable(service, "lang");

        assertThat(common.sharedConflict("common", "undo", "shortcut U")).isEqualTo("Scope — Toggle case");
        assertThat(common.sharedConflict("common", "undo", "shortcut Q")).isNull();
        assertThat(common.sharedConflict("common", "undo", "")).isNull();

        assertThat(lang.sharedConflict("s", "a", "shortcut Z")).isEqualTo("General — Undo");
        assertThat(lang.sharedConflict("s", "a", "shortcut U")).isNull();
    }

    @Test
    void sharedConflictsAreWarningsAndDoNotBlockAssignment() {
        KeymapServiceImpl service = service();
        KeymapConfigurable common = new KeymapConfigurable(service, "editor");

        assertThat(common.setShortcut("common", "undo", "shortcut U")).isTrue();
        assertThat(common.shortcutOf("common", "undo")).isEqualTo("shortcut U");
    }

    @Test
    void serviceExposesScopesByLanguage() {
        assertThat(service().scopesByLanguage()).containsOnlyKeys("editor", "lang", "other");
    }
}
