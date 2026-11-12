package ru.mcs.core.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.action.DefaultActionGroup;

class ActionManagerImplTest {

    private ActionManagerImpl am;

    @BeforeEach
    void setUp() {
        am = new ActionManagerImpl();
        am.registerAction("g", new DefaultActionGroup("G", true));
    }

    private void reg(String id) {
        am.registerAction(id, new TestAction(id));
    }

    @Test
    void duplicateIdIsRejected() {
        reg("a");
        assertThatThrownBy(() -> reg("a")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameInstanceUnderTwoIdsIsRejected() {
        TestAction t = new TestAction("t");
        am.registerAction("t1", t);
        assertThatThrownBy(() -> am.registerAction("t2", t)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constraintsControlOrder() {
        reg("a");
        reg("b");
        reg("c");
        reg("d");
        reg("e");
        am.addToGroup("g", "a", Constraints.LAST);
        am.addToGroup("g", "b", Constraints.LAST);
        am.addToGroup("g", "c", Constraints.FIRST);
        am.addToGroup("g", "d", Constraints.before("a"));
        am.addToGroup("g", "e", Constraints.after("a"));
        assertThat(am.childIds("g")).containsExactly("c", "d", "a", "e", "b");
    }

    @Test
    void unknownRelativeIdAppends() {
        reg("a");
        reg("b");
        am.addToGroup("g", "a", Constraints.LAST);
        am.addToGroup("g", "b", Constraints.before("missing"));
        assertThat(am.childIds("g")).containsExactly("a", "b");
    }

    @Test
    void duplicateAddToGroupIsIgnored() {
        reg("a");
        am.addToGroup("g", "a", Constraints.LAST);
        am.addToGroup("g", "a", Constraints.LAST);
        assertThat(am.childIds("g")).containsExactly("a");
    }

    @Test
    void addToUnknownGroupFails() {
        reg("a");
        assertThatThrownBy(() -> am.addToGroup("nope", "a", Constraints.LAST))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> am.addToGroup("g", "nope", Constraints.LAST))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unregisterRemovesFromGroups() {
        reg("a");
        am.addToGroup("g", "a", Constraints.LAST);
        am.unregisterAction("a");
        assertThat(am.childIds("g")).isEmpty();
        assertThat(am.getAction("a")).isNull();
    }

    @Test
    void scopedCleanupRemovesPluginActionsAndLinks() {
        reg("core.action");
        reg("core.other");
        am.addToGroup("g", "core.action", Constraints.LAST);

        ActionManager scoped = am.scopedFor("p1");
        scoped.registerAction("p1.act", new TestAction("x"));
        scoped.addToGroup("g", "p1.act", Constraints.LAST);
        scoped.addToGroup("g", "core.other", Constraints.FIRST);
        assertThat(am.pluginActionIds()).containsExactly("p1.act");

        am.unregisterAll("p1");

        assertThat(am.getAction("p1.act")).isNull();
        assertThat(am.getAction("core.other")).isNotNull();
        assertThat(am.childIds("g")).containsExactly("core.action");
        assertThat(am.pluginActionIds()).isEmpty();
    }

    @Test
    void contextUpdateNotifiesListeners() {
        AtomicInteger n = new AtomicInteger();
        am.addChangeListener(n::incrementAndGet);

        am.updateActions();

        assertThat(n.get()).isEqualTo(1);
    }

    @Test
    void changeListenerFiresOnChangesAndCanBeRemoved() {
        AtomicInteger n = new AtomicInteger();
        Runnable remove = am.addChangeListener(n::incrementAndGet);
        reg("a");
        am.addToGroup("g", "a", Constraints.LAST);
        assertThat(n.get()).isEqualTo(2);
        remove.run();
        reg("b");
        assertThat(n.get()).isEqualTo(2);
    }
}
