package ru.mcs.markdown.editor.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.markdown.api.BlockKind;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.action.AnActionEvent;

class EditorContextMenuBuilderTest {

    private final TestActionManager actions = TestActionManager.editorActions();
    private final EditorContextMenuBuilder builder = new EditorContextMenuBuilder(actions);

    @Test
    void offersClipboardQuickFormatParagraphTypeAndInsertCommandsForEveryBlock() {
        List<String> expected = new ArrayList<>(List.of(EditorActionIds.CUT, EditorActionIds.COPY,
                EditorActionIds.PASTE, EditorActionIds.PASTE_PLAIN, EditorActionIds.PARAGRAPH, EditorActionIds.IMAGE,
                EditorActionIds.HORIZONTAL_RULE, EditorActionIds.TABLE, EditorActionIds.CODE_BLOCK,
                EditorActionIds.MATH_BLOCK, EditorActionIds.LINK));
        expected.addAll(EditorContextMenuBuilder.QUICK_FORMAT);
        for (int level = 1; level <= 6; level++) {
            expected.add(EditorActionIds.heading(level));
        }

        assertThat(builder.actionIds(EditorBlockContext.paragraph())).containsAll(expected);
        assertThat(builder.actionIds(new EditorBlockContext(BlockKind.HEADING, false, false, false, 2)))
                .isEqualTo(builder.actionIds(EditorBlockContext.paragraph()));
        assertThat(builder.actionIds(EditorBlockContext.paragraph()))
                .doesNotContain(EditorActionIds.UNDO, EditorActionIds.SELECT_ALL, EditorActionIds.WORD_COUNT);
    }

    @Test
    void headingLevelIsOnlyKeptForHeadingsAndClamped() {
        assertThat(new EditorBlockContext(BlockKind.HEADING, false, false, false, 9).headingLevel()).isEqualTo(6);
        assertThat(new EditorBlockContext(BlockKind.HEADING, false, false, false, 0).headingLevel()).isEqualTo(1);
        assertThat(new EditorBlockContext(BlockKind.PARAGRAPH, false, false, false, 3).headingLevel()).isZero();
        assertThat(EditorBlockContext.paragraph().headingLevel()).isZero();
    }

    @Test
    void popupHasThreeTyporaAreasInOrder() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPopupMenu popup = builder.build(EditorBlockContext.paragraph());

            assertThat(popup.getComponentCount()).isEqualTo(9);
            assertThat(names(popup, 0, 3)).containsExactly(EditorActionIds.CUT, EditorActionIds.COPY, EditorActionIds.PASTE);
            assertThat(popup.getComponent(3)).isInstanceOf(JMenu.class);
            assertThat(popup.getComponent(4)).isInstanceOf(JPopupMenu.Separator.class);
            assertThat(popup.getComponent(5)).isInstanceOf(JPanel.class);
            assertThat(popup.getComponent(6)).isInstanceOf(JPopupMenu.Separator.class);
            assertThat(popup.getComponent(7)).isInstanceOf(JMenu.class);
            assertThat(popup.getComponent(8)).isInstanceOf(JMenu.class);
        });
    }

    @Test
    void quickButtonsFollowTyporaOrderAndRunTheirAction() throws Exception {
        AtomicInteger bold = new AtomicInteger();
        actions.registerAction(EditorActionIds.BOLD, new AnAction(EditorActionIds.BOLD) {
            @Override
            public void actionPerformed(AnActionEvent event) {
                bold.incrementAndGet();
            }
        });
        SwingUtilities.invokeAndWait(() -> {
            JPopupMenu popup = builder.build(EditorBlockContext.paragraph());
            JPanel panel = (JPanel) popup.getComponent(5);

            List<String> ids = new ArrayList<>();
            for (Component component : panel.getComponents()) {
                ids.add(component.getName());
            }
            assertThat(ids).containsExactlyElementsOf(EditorContextMenuBuilder.QUICK_FORMAT);
            ((JButton) panel.getComponent(0)).doClick();
            assertThat(bold).hasValue(1);
        });
    }

    @Test
    void paragraphTypeMenuShowsCurrentHeadingAndChecksIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JMenu type = (JMenu) builder.build(new EditorBlockContext(BlockKind.HEADING, false, false, false, 2))
                    .getComponent(7);

            assertThat(type.getText()).isEqualTo(EditorActionIds.heading(2));
            List<JCheckBoxMenuItem> items = checks(type);
            assertThat(items).hasSize(7);
            assertThat(items.stream().filter(JCheckBoxMenuItem::isSelected).map(Component::getName))
                    .containsExactly(EditorActionIds.heading(2));
            assertThat(items.getLast().getName()).isEqualTo(EditorActionIds.PARAGRAPH);
        });
    }

    @Test
    void paragraphTypeMenuChecksParagraphForNonHeadings() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JMenu type = (JMenu) builder.build(EditorBlockContext.paragraph()).getComponent(7);

            assertThat(type.getText()).isEqualTo(EditorActionIds.PARAGRAPH);
            assertThat(checks(type).stream().filter(JCheckBoxMenuItem::isSelected).map(Component::getName))
                    .containsExactly(EditorActionIds.PARAGRAPH);
        });
    }

    @Test
    void insertMenuListsImageThenBlocks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JMenu insert = (JMenu) builder.build(EditorBlockContext.paragraph()).getComponent(8);

            List<String> ids = new ArrayList<>();
            for (Component component : insert.getMenuComponents()) {
                if (component instanceof JMenuItem item) {
                    ids.add(item.getName());
                }
            }
            assertThat(ids).containsExactly(EditorActionIds.IMAGE, EditorActionIds.LINK,
                    EditorActionIds.HORIZONTAL_RULE, EditorActionIds.TABLE, EditorActionIds.CODE_BLOCK,
                    EditorActionIds.MATH_BLOCK);
        });
    }

    private static List<String> names(JPopupMenu popup, int from, int to) {
        List<String> names = new ArrayList<>();
        for (int index = from; index < to; index++) {
            names.add(popup.getComponent(index).getName());
        }
        return names;
    }

    private static List<JCheckBoxMenuItem> checks(JMenu menu) {
        List<JCheckBoxMenuItem> items = new ArrayList<>();
        for (Component component : menu.getMenuComponents()) {
            if (component instanceof JCheckBoxMenuItem item) {
                items.add(item);
            }
        }
        return items;
    }
}
