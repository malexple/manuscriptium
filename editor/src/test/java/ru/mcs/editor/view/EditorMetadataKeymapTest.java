package ru.mcs.editor.view;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.mcs.editor.action.EditorMetadataProvider;
import ru.mcs.metadata.MetadataCatalog;
import ru.mcs.metadata.MetadataDocument;

class EditorMetadataKeymapTest {

    @Test
    void clipboardUndoAndRedoBindingsComeFromEditorResource() {
        MetadataDocument document = MetadataCatalog.from(new EditorMetadataProvider()).get("editor").document();

        assertThat(document.keymap()).hasSize(6);
        assertThat(document.keymap()).extracting(MetadataDocument.KeyBinding::keystroke)
                .containsExactly("shortcut X", "shortcut C", "shortcut V", "shortcut Z",
                        "shift shortcut Z", "shortcut Y");
        assertThat(document.keymap()).allMatch(binding -> binding.applicability().targets().contains("desktop"));
    }
}
