package ru.mcs.core.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.swing.editor.style.ColorSchemeService;
import ru.mcs.spi.swing.editor.style.TokenDefinition;
import ru.mcs.spi.swing.editor.style.TokenGroup;
import ru.mcs.spi.swing.editor.style.TokenSpan;

class ColorSchemeServiceImplTest {

    static TokenGroup group() {
        return new TokenGroup("sample", "Sample", List.of(new TokenDefinition("t", "Token", 0xFF0000, 0x00FF00)),
                "ab", text -> List.of(new TokenSpan(0, 1, "t")));
    }

    @Test
    void defaultsOverridesAndUnknownTokens() {
        ColorSchemeServiceImpl service = new ColorSchemeServiceImpl();
        service.registerGroup(group());

        assertThat(service.color("t", false)).isEqualTo(new Color(0xFF0000));
        assertThat(service.color("t", true)).isEqualTo(new Color(0x00FF00));
        assertThat(service.color("missing", false)).isNull();

        service.setOverrides(Map.of(ColorSchemeService.key("t", true), 0x112233));
        assertThat(service.color("t", true)).isEqualTo(new Color(0x112233));
        assertThat(service.color("t", false)).isEqualTo(new Color(0xFF0000));

        service.unregisterGroup("sample");
        assertThat(service.color("t", false)).isNull();
        assertThat(service.groups()).isEmpty();
    }

    @Test
    void listenersAreNotifiedOnlyWhenSomethingChanged() {
        ColorSchemeServiceImpl service = new ColorSchemeServiceImpl();
        List<String> events = new ArrayList<>();
        Runnable listener = () -> events.add("changed");
        service.addChangeListener(listener);

        service.registerGroup(group());
        service.setOverrides(Map.of(ColorSchemeService.key("t", false), 5));
        service.setOverrides(Map.of(ColorSchemeService.key("t", false), 5));
        service.removeChangeListener(listener);
        service.setOverrides(Map.of());

        assertThat(events).hasSize(2);
    }

    @Test
    void invalidKeysAndValuesAreDropped() {
        ColorSchemeServiceImpl service = new ColorSchemeServiceImpl();

        service.setOverrides(Map.of("t:blue", 5, ColorSchemeService.key("t", false), -1,
                ColorSchemeService.key("u", false), 0x1000000, ColorSchemeService.key("ok", false), 0xABCDEF));

        assertThat(service.overrides()).containsOnlyKeys(ColorSchemeService.key("ok", false));
    }

    @Test
    void stateRoundTripsAndToleratesOtherNumberTypes() {
        ColorSchemeServiceImpl service = new ColorSchemeServiceImpl();
        service.setOverrides(Map.of(ColorSchemeService.key("t", false), 0x123456));
        ColorSchemeServiceImpl restored = new ColorSchemeServiceImpl();

        restored.loadState(service.getState());
        assertThat(restored.overrides()).isEqualTo(service.overrides());

        ColorSchemeServiceImpl loose = new ColorSchemeServiceImpl();
        loose.loadState(Map.of("overrides", Map.of(ColorSchemeService.key("a", true), 255L,
                ColorSchemeService.key("b", true), 65280.0, "junk", "text")));
        assertThat(loose.overrides()).containsEntry(ColorSchemeService.key("a", true), 255)
                .containsEntry(ColorSchemeService.key("b", true), 65280).hasSize(2);
        loose.loadState(Map.of("overrides", "broken"));
        assertThat(loose.overrides()).hasSize(2);
    }
}
