package ru.mcs.core.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.swing.editor.keymap.KeymapService;
import ru.mcs.spi.swing.editor.keymap.ShortcutBinding;
import ru.mcs.spi.swing.editor.keymap.ShortcutScope;

class KeymapServiceImplTest {

    static ShortcutScope scope() {
        return new ShortcutScope("s", "Scope", List.of(
                new ShortcutBinding("a", "Action A", "shortcut L"),
                new ShortcutBinding("b", "Action B", "shortcut K"),
                new ShortcutBinding("c", "Action C", "")));
    }

    @Test
    void defaultsOverridesAndUnknownActions() {
        KeymapServiceImpl service = new KeymapServiceImpl();
        service.registerScope("lang", scope());

        assertThat(service.shortcut("s", "a")).isEqualTo("shortcut L");
        assertThat(service.shortcut("s", "c")).isEmpty();
        assertThat(service.shortcut("s", "missing")).isEmpty();

        service.setOverrides(Map.of(KeymapService.key("s", "a"), "alt 1", KeymapService.key("s", "b"), ""));
        assertThat(service.shortcut("s", "a")).isEqualTo("alt 1");
        assertThat(service.shortcut("s", "b")).isEmpty();
        assertThat(service.scopes("lang")).hasSize(1);

        service.unregisterScopes("lang");
        assertThat(service.scopes("lang")).isEmpty();
        assertThat(service.shortcut("s", "c")).isEmpty();
    }

    @Test
    void listenersAreNotifiedOnlyOnRealChanges() {
        KeymapServiceImpl service = new KeymapServiceImpl();
        List<String> events = new ArrayList<>();
        Runnable listener = () -> events.add("changed");
        service.addChangeListener(listener);

        service.registerScope("lang", scope());
        service.setOverrides(Map.of(KeymapService.key("s", "a"), "alt 1"));
        service.setOverrides(Map.of(KeymapService.key("s", "a"), "alt 1"));
        service.removeChangeListener(listener);
        service.setOverrides(Map.of());

        assertThat(events).hasSize(2);
    }

    @Test
    void stateRoundTripsAndDropsGarbage() {
        KeymapServiceImpl service = new KeymapServiceImpl();
        service.setOverrides(Map.of(KeymapService.key("s", "a"), "alt 1", "noSeparator", "x"));
        assertThat(service.overrides()).containsOnlyKeys(KeymapService.key("s", "a"));

        KeymapServiceImpl restored = new KeymapServiceImpl();
        restored.loadState(service.getState());
        assertThat(restored.overrides()).isEqualTo(service.overrides());

        restored.loadState(Map.of("overrides", Map.of("s|b", 5, "s|c", "ctrl C")));
        assertThat(restored.overrides()).containsOnlyKeys("s|c");
        restored.loadState(Map.of("overrides", "broken"));
        assertThat(restored.overrides()).containsOnlyKeys("s|c");
    }
}
