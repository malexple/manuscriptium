package ru.mcs.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.InputEvent;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import ru.mcs.editor.view.CommonKeymap;
import ru.mcs.spi.swing.editor.keymap.ShortcutBinding;
import ru.mcs.spi.swing.editor.keymap.ShortcutScope;
import ru.mcs.spi.swing.editor.keymap.Shortcuts;
import ru.mcs.spi.swing.editor.style.TokenDefinition;
import ru.mcs.spi.swing.editor.style.TokenGroup;
import ru.mcs.spi.swing.editor.style.TokenSpan;

class YamlPlatformIntegrationTest {

    @Test
    void colorGroupDescribesEveryTokenAndShowsThemInThePreview() {
        TokenGroup group = YamlColorScheme.group(key -> key);
        YamlTokenColors defaults = new YamlTokenColors();

        assertThat(group.tokens()).extracting(TokenDefinition::id).containsExactlyElementsOf(YamlTokens.ALL);
        for (TokenDefinition definition : group.tokens()) {
            assertThat(definition.rgb(false)).isEqualTo(defaults.color(definition.id(), false).getRGB() & 0xFFFFFF);
        }
        assertThat(group.preview().highlight(group.sample())).extracting(TokenSpan::token)
                .containsAll(YamlTokens.ALL);
    }

    @Test
    void keymapScopeHasParsableDistinctDefaults() {
        ShortcutScope scope = YamlKeymapScopes.scope(key -> key);
        Set<KeyStroke> seen = new HashSet<>();

        assertThat(scope.id()).isEqualTo(YamlKeymapScopes.SCOPE);
        assertThat(scope.displayName()).isEqualTo("Editor");
        for (ShortcutBinding binding : scope.bindings()) {
            assertThat(seen.add(Shortcuts.parse(binding.defaultShortcut(), InputEvent.CTRL_DOWN_MASK)))
                    .as(binding.actionId()).isTrue();
        }
        assertThat(scope.bindings()).extracting(ShortcutBinding::actionId)
                .containsExactly("toggleComment", "reformat", "validate");
    }

    @Test
    void bundlesCoverTokensCommandsAndCommonKeysInBothLanguages() throws IOException {
        List<String> required = new java.util.ArrayList<>();
        YamlTokens.ALL.forEach(token -> required.add("token." + token));
        for (ShortcutBinding binding : CommonKeymap.scope(key -> key).bindings()) {
            required.add("keys.common." + binding.actionId());
        }
        for (ShortcutBinding binding : YamlKeymapScopes.defaults()) {
            required.add("keys.yaml." + binding.actionId());
        }
        required.addAll(List.of("keys.scope.editor", "keys.scope.common", "settings.yaml.language",
                "settings.yaml.lineNumbers", "settings.yaml.highlighting", "settings.yaml.validate",
                "settings.yaml.strictKeys", "codestyle.yaml.indent", "codestyle.yaml.tabs", "codestyle.yaml.trim",
                "codestyle.yaml.finalNewline", "codestyle.yaml.maxBlank", "status.valid", "status.line",
                "status.column", "status.skipped", "status.loading"));
        for (String resource : List.of("messages.properties", "messages_ru.properties")) {
            Properties properties = new Properties();
            try (var stream = YamlPlatformIntegrationTest.class.getResourceAsStream(
                    "/ru/mcs/yaml/messages/" + resource)) {
                assertThat(stream).as(resource).isNotNull();
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
            for (String key : required) {
                assertThat(properties.getProperty(key)).as(resource + " " + key).isNotBlank();
            }
        }
    }
}
