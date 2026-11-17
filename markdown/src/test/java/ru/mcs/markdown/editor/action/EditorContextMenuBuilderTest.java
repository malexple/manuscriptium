package ru.mcs.markdown.editor.action;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.markdown.api.BlockKind;

class EditorContextMenuBuilderTest {

    private final TestActionManager actions = TestActionManager.editorActions();
    private final EditorContextMenuBuilder builder = new EditorContextMenuBuilder(actions);

    @Test
    void paragraphContainsCommonGroupsButNoBlockSpecificCommand() {
        assertThat(builder.actionIds(EditorBlockContext.paragraph()))
                .contains(EditorActionIds.CUT, EditorActionIds.COPY, EditorActionIds.PARAGRAPH_MENU,
                        EditorActionIds.FORMAT_MENU, EditorActionIds.INSERT_MENU, EditorActionIds.WORD_COUNT)
                .doesNotContain(EditorActionIds.CODE_BLOCK, EditorActionIds.TABLE, EditorActionIds.MATH_BLOCK,
                        EditorActionIds.IMAGE, EditorActionIds.LINK);
    }

    @Test
    void blockTypeChoosesConditionalAction() {
        assertThat(builder.actionIds(new EditorBlockContext(BlockKind.FENCED_CODE, false, false, false)))
                .contains(EditorActionIds.CODE_BLOCK);
        assertThat(builder.actionIds(new EditorBlockContext(BlockKind.TABLE, false, false, false)))
                .contains(EditorActionIds.TABLE);
        assertThat(builder.actionIds(new EditorBlockContext(BlockKind.MATH_BLOCK, false, false, false)))
                .contains(EditorActionIds.MATH_BLOCK);
        assertThat(builder.actionIds(new EditorBlockContext(BlockKind.PARAGRAPH, false, false, true)))
                .contains(EditorActionIds.IMAGE);
        assertThat(builder.actionIds(new EditorBlockContext(BlockKind.PARAGRAPH, true, true, false)))
                .contains(EditorActionIds.LINK);
    }

    @Test
    void buildsSwingPopupFromActionGroups() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPopupMenu popup = builder.build(new EditorBlockContext(BlockKind.TABLE, true, false, false));
            assertThat(popup.getComponentCount()).isGreaterThan(8);
            assertThat(popup.getComponent(0)).isInstanceOf(javax.swing.JMenuItem.class);
        });
    }
}
