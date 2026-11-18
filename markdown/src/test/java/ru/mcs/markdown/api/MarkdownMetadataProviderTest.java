package ru.mcs.markdown.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.markdown.editor.action.EditorActionIds;
import ru.mcs.metadata.MetadataCatalog;
import ru.mcs.metadata.MetadataDocument;
import ru.mcs.metadata.MetadataProvider;
import ru.mcs.metadata.TargetRule;

class MarkdownMetadataProviderTest {

    @Test
    void declaresAllActionsGroupsParentsAndPanelsWithoutCoreDependency() {
        MetadataCatalog catalog = MetadataCatalog.from(new MarkdownMetadataProvider(), new CoreParentsProvider());
        MetadataDocument document = catalog.get("markdown").document();

        assertThat(document.actions()).extracting(MetadataDocument.Action::id)
                .containsAll(EditorActionIds.ALL)
                .contains("notes.copyWikiLink", "toolwindow.toggle.properties.panel", "toolwindow.toggle.notes.panel");
        assertThat(document.menus()).extracting(MetadataDocument.Menu::id).containsExactly(
                EditorActionIds.PARAGRAPH_MENU, EditorActionIds.FORMAT_MENU,
                EditorActionIds.INSERT_MENU, "MarkdownEditor.TableMenu");
        MetadataDocument.Action toggle = document.actions().stream()
                .filter(action -> action.id().equals(EditorActionIds.TOGGLE_MODE)).findFirst().orElseThrow();
        assertThat(toggle.parents()).containsExactly("MainMenu.View", "MainToolbar", "EditorTabPopup");
        assertThat(document.panels()).extracting(MetadataDocument.Panel::id)
                .containsExactly("properties.panel", "notes.panel");
        assertThat(document.settingsPages()).extracting(MetadataDocument.SettingsPage::id)
                .containsExactly("markdown.editor.settings");
    }

    private static final class CoreParentsProvider implements MetadataProvider {
        @Override
        public String id() {
            return "core-test-parents";
        }

        @Override
        public String resource() {
            return "/core-test-parents.json";
        }

        @Override
        public java.io.InputStream open() {
            return new java.io.ByteArrayInputStream("""
                    {"formatVersion":1,"menus":[
                      {"id":"MainMenu"},{"id":"MainMenu.View"},{"id":"MainMenu.Tools"},
                      {"id":"EditorTabPopup"}
                    ],"toolbars":[{"id":"MainToolbar"}]}
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
