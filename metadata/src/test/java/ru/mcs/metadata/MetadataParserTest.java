package ru.mcs.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MetadataParserTest {

    @Test
    void parsesAndValidatesEverySection() {
        MetadataDocument document = MetadataParser.parse("""
                {
                  "formatVersion": 1,
                  "actions": [{"id": "app.open", "textKey": "open", "parents": ["MainToolbar"]}],
                  "menus": [{"id": "MainMenu", "children": [
                    {"command": "app.open", "position": "first"},
                    {"separator": "app.separator", "position": "after", "anchor": "app.open"}
                  ]}],
                  "toolbars": [{"id": "MainToolbar", "children": [{"command": "app.open"}]}],
                  "statusBar": [{"id": "app.status", "command": "app.open", "alignment": "right"}],
                  "settingsPages": [{"id": "app.settings", "titleKey": "settings", "fields": [{
                    "id": "language", "type": "choice", "labelKey": "language", "stateKey": "language",
                    "options": [{"value": "en", "labelKey": "en"}, {"value": "ru", "labelKey": "ru"}]
                  }]}],
                  "keymap": [{"command": "app.open", "keystroke": "shortcut O"}],
                  "panels": [{"id": "app.panel", "titleKey": "panel", "command": "app.open", "anchor": "left"}]
                }
                """);
        MetadataValidator.validate(Map.of("test", document));

        assertThat(document.formatVersion()).isEqualTo(1);
        assertThat(document.actions()).hasSize(1);
        assertThat(document.menus().getFirst().children().get(1).isSeparator()).isTrue();
        assertThat(document.settingsPages().getFirst().fields().getFirst().type())
                .isEqualTo(MetadataDocument.FieldType.CHOICE);
        assertThat(document.panels().getFirst().anchor()).isEqualTo("left");
    }

    @Test
    void rejectsDuplicateIdsAndUnknownCrossReferences() {
        MetadataDocument duplicate = MetadataParser.parse("""
                {"formatVersion":1,"actions":[{"id":"same"},{"id":"same"}]}
                """);
        assertThatThrownBy(() -> MetadataValidator.validate(Map.of("test", duplicate)))
                .isInstanceOf(MetadataException.class).hasMessageContaining("Duplicate command ID");

        MetadataDocument missing = MetadataParser.parse("""
                {"formatVersion":1,"keymap":[{"command":"absent","keystroke":"shortcut A"}]}
                """);
        assertThatThrownBy(() -> MetadataValidator.validate(Map.of("test", missing)))
                .isInstanceOf(MetadataException.class).hasMessageContaining("Unknown keymap command");
    }

    @Test
    void rejectsUnknownVersionTargetFieldTypeAndDuplicateJsonKey() {
        MetadataDocument unsupportedVersion = MetadataParser.parse("""
                {"formatVersion":2}
                """);
        assertThatThrownBy(() -> MetadataValidator.validate(Map.of("test", unsupportedVersion)))
                .isInstanceOf(MetadataException.class).hasMessageContaining("Unsupported formatVersion");

        MetadataDocument unknownTarget = MetadataParser.parse("""
                {"formatVersion":1,"actions":[{"id":"app.open","targets":["plan9"]}]}
                """);
        assertThatThrownBy(() -> MetadataValidator.validate(Map.of("test", unknownTarget)))
                .isInstanceOf(MetadataException.class).hasMessageContaining("Unknown target");

        assertThatThrownBy(() -> MetadataParser.parse("""
                {"formatVersion":1,"settingsPages":[{"id":"app.settings","titleKey":"title","fields":[
                  {"id":"value","type":"date","labelKey":"value","stateKey":"value"}
                ]}]}
                """)).isInstanceOf(MetadataException.class).hasMessageContaining("Unknown settings field type");

        assertThatThrownBy(() -> MetadataParser.parse("""
                {"formatVersion":1,"formatVersion":1}
                """)).isInstanceOf(MetadataException.class).hasMessageContaining("Duplicate object key");
    }
}
