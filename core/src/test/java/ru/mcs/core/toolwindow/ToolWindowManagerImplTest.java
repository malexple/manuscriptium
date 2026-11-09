package ru.mcs.core.toolwindow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import javax.swing.JLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.core.action.CoreActions;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.ui.ToolWindowAnchor;
import ru.mcs.spi.ui.ToolWindowSpec;

class ToolWindowManagerImplTest {

    @TempDir
    Path tmp;

    private ActionManagerImpl actions;
    private ToolWindowManagerImpl m;

    @BeforeEach
    void setUp() {
        actions = new ActionManagerImpl();
        CoreActions.registerStandardGroups(actions);
        m = new ToolWindowManagerImpl(actions, LayoutStore.inMemory());
    }

    private static ToolWindowSpec spec(String id, ToolWindowAnchor anchor, boolean shown) {
        return ToolWindowSpec.of(id, id, anchor, () -> new JLabel(id)).withShowByDefault(shown);
    }

    @Test
    void onlyOnePanelIsActivePerAnchor() {
        ToolWindowImpl a = m.register("p", spec("a", ToolWindowAnchor.LEFT, true));
        ToolWindowImpl b = m.register("p", spec("b", ToolWindowAnchor.LEFT, true));
        ToolWindowImpl c = m.register("p", spec("c", ToolWindowAnchor.BOTTOM, true));
        assertThat(a.isVisible()).isTrue();
        assertThat(b.isVisible()).isFalse();
        assertThat(c.isVisible()).isTrue();
    }

    @Test
    void showDisplacesOtherPanelOnSameSide() {
        ToolWindowImpl a = m.register("p", spec("a", ToolWindowAnchor.LEFT, true));
        ToolWindowImpl b = m.register("p", spec("b", ToolWindowAnchor.LEFT, false));
        b.show();
        assertThat(a.isVisible()).isFalse();
        assertThat(b.isVisible()).isTrue();
        assertThat(m.activeAt(ToolWindowAnchor.LEFT)).isSameAs(b);
    }

    @Test
    void toggleAndHide() {
        ToolWindowImpl a = m.register("p", spec("a", ToolWindowAnchor.RIGHT, false));
        a.toggle();
        assertThat(a.isVisible()).isTrue();
        a.toggle();
        assertThat(a.isVisible()).isFalse();
        a.hide();
        assertThat(m.activeAt(ToolWindowAnchor.RIGHT)).isNull();
    }

    @Test
    void duplicateIdIsRejectedAndLeavesNoGarbage() {
        m.register("p", spec("a", ToolWindowAnchor.LEFT, false));
        assertThatThrownBy(() -> m.register("p", spec("a", ToolWindowAnchor.RIGHT, false)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(m.ids()).containsExactly("a");
    }

    @Test
    void listenersAreNotifiedAboutChanges() {
        int[] n = {0};
        m.addChangeListener(() -> n[0]++);
        ToolWindowImpl a = m.register("p", spec("a", ToolWindowAnchor.LEFT, false));
        a.show();
        a.hide();
        assertThat(n[0]).isGreaterThanOrEqualTo(3);
    }

    @Test
    void layoutIsRestoredFromFile() {
        Path file = tmp.resolve("layout.properties");
        ToolWindowManagerImpl first = new ToolWindowManagerImpl(actions, LayoutStore.at(file));
        ToolWindowImpl a = first.register("p", spec("a", ToolWindowAnchor.LEFT, true));
        a.hide();
        first.setSize(ToolWindowAnchor.LEFT, 333);
        first.save();

        ActionManagerImpl otherActions = new ActionManagerImpl();
        CoreActions.registerStandardGroups(otherActions);
        ToolWindowManagerImpl second = new ToolWindowManagerImpl(otherActions, LayoutStore.at(file));
        ToolWindowImpl restored = second.register("p", spec("a", ToolWindowAnchor.LEFT, true));

        assertThat(restored.isVisible()).as("saved 'hidden' wins over showByDefault").isFalse();
        assertThat(second.size(ToolWindowAnchor.LEFT)).isEqualTo(333);
        assertThat(second.size(ToolWindowAnchor.BOTTOM)).isEqualTo(200);
    }

    @Test
    void toggleActionLivesInViewMenuAndDisappearsWithOwner() {
        m.register("p", spec("a", ToolWindowAnchor.LEFT, false));
        assertThat(actions.childIds(ActionIds.VIEW_MENU)).containsExactly("toolwindow.toggle.a");

        m.unregisterOwner("p");
        assertThat(m.ids()).isEmpty();
        assertThat(actions.childIds(ActionIds.VIEW_MENU)).isEmpty();
    }

    @Test
    void unregisterOwnerKeepsSavedVisibility() {
        Path file = tmp.resolve("layout.properties");
        LayoutStore store = LayoutStore.at(file);
        ToolWindowManagerImpl mgr = new ToolWindowManagerImpl(actions, store);
        ToolWindowImpl a = mgr.register("p", spec("a", ToolWindowAnchor.LEFT, false));
        a.show();
        mgr.unregisterOwner("p");
        assertThat(store.visible("a")).contains(true);
    }

    @Test
    void foreignOwnerCannotUnregister() {
        m.register("p1", spec("a", ToolWindowAnchor.LEFT, false));
        assertThatThrownBy(() -> m.unregister("p2", "a")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failingContentFactoryDoesNotThrow() {
        ToolWindowImpl bad = m.register("p", ToolWindowSpec.of("bad", "Bad", ToolWindowAnchor.LEFT, () -> {
            throw new IllegalStateException("plugin bug");
        }));
        assertThat(bad.content()).isNotNull();
    }
}
