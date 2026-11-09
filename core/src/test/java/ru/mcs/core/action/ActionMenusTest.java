package ru.mcs.core.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.action.DataContext;
import ru.mcs.spi.action.DefaultActionGroup;
import ru.mcs.spi.action.Separator;

class ActionMenusTest {

    private static List<String> describe(List<JComponent> items) {
        return items.stream().map(c -> c instanceof JMenuItem mi ? mi.getText() : "-").toList();
    }

    private static List<JComponent> build(DefaultActionGroup g) {
        return ActionMenus.buildItems(g, "test", () -> DataContext.EMPTY);
    }

    private static AnAction hidden(String text) {
        return new TestAction(text) {
            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setVisible(false);
            }
        };
    }

    @Test
    void collapsesSeparatorsAndSkipsHiddenActions() {
        DefaultActionGroup inline = new DefaultActionGroup("inline", false);
        inline.add(new TestAction("C"));

        DefaultActionGroup g = new DefaultActionGroup();
        g.addSeparator();
        g.add(new TestAction("A"));
        g.addSeparator();
        g.addSeparator();
        g.add(hidden("H"));
        g.add(new TestAction("B"));
        g.add(inline);
        g.addSeparator();

        assertThat(describe(build(g))).containsExactly("A", "-", "B", "C");
    }

    @Test
    void popupSubgroupBecomesMenuAndEmptyGroupsAreHidden() {
        DefaultActionGroup sub = new DefaultActionGroup("Sub", true);
        sub.add(new TestAction("X"));
        DefaultActionGroup empty = new DefaultActionGroup("Empty", true);

        DefaultActionGroup g = new DefaultActionGroup();
        g.add(sub);
        g.add(empty);

        List<JComponent> items = build(g);
        assertThat(describe(items)).containsExactly("Sub");
        assertThat(items.get(0)).isInstanceOf(JMenu.class);
    }

    @Test
    void failingUpdateDisablesButDoesNotBreakMenu() {
        DefaultActionGroup g = new DefaultActionGroup();
        g.add(new TestAction("Boom") {
            @Override
            public void update(AnActionEvent e) {
                throw new IllegalStateException("plugin bug");
            }
        });
        g.add(new TestAction("Fine"));

        List<JComponent> items = build(g);
        assertThat(describe(items)).containsExactly("Boom", "Fine");
        assertThat(items.get(0).isEnabled()).isFalse();
        assertThat(items.get(1).isEnabled()).isTrue();
    }

    @Test
    void updateChangesTextAtBuildTime() {
        int[] clicks = {0};
        DefaultActionGroup g = new DefaultActionGroup();
        g.add(new TestAction("Counter") {
            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setText("Clicked " + clicks[0]);
            }
        });
        assertThat(describe(build(g))).containsExactly("Clicked 0");
        clicks[0] = 3;
        assertThat(describe(build(g))).containsExactly("Clicked 3");
    }

    @Test
    void separatorSingletonIsRecognised() {
        DefaultActionGroup g = new DefaultActionGroup();
        g.add(new TestAction("A"));
        g.add(Separator.INSTANCE);
        g.add(new TestAction("B"));
        assertThat(describe(build(g))).containsExactly("A", "-", "B");
    }
}
