package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ViewOperationsTest {

    @Test
    void cloneAddsSameLinkToRootWithoutRemovingSource() {
        ViewDefinition view = view();

        boolean changed = ViewOperations.moveOrClone(view, List.of(0, 0), List.of(), true);

        assertThat(changed).isTrue();
        assertThat(view.entries().get(0).children()).hasSize(1);
        assertThat(view.entries().get(2).file()).isEqualTo("a.md");
        assertThat(view.entries().get(0).children().getFirst())
                .isSameAs(view.entries().get(2));
    }

    @Test
    void moveRemovesSourceAndAppendsToNestedTarget() {
        ViewDefinition view = view();

        boolean changed = ViewOperations.moveOrClone(view, List.of(0, 0), List.of(1), false);

        assertThat(changed).isTrue();
        assertThat(view.entries().get(0).children()).isEmpty();
        assertThat(view.entries()).hasSize(2);
        assertThat(view.entries().get(1).children()).extracting(ViewEntry::file)
                .containsExactly("b.md", "a.md");
    }

    @Test
    void moveFromNestedBranchToRootUsesStableTargetObject() {
        ViewDefinition view = view();

        boolean changed = ViewOperations.moveOrClone(view, List.of(0, 0), List.of(), false);

        assertThat(changed).isTrue();
        assertThat(view.entries().get(0).children()).isEmpty();
        assertThat(view.entries()).extracting(ViewEntry::file)
                .containsExactly(null, null, "a.md");
    }

    @Test
    void rejectsDuplicateInvalidPathsNodesAndSameParent() {
        ViewDefinition duplicate = view();
        assertThat(ViewOperations.moveOrClone(duplicate, List.of(0, 0), List.of(0), true)).isFalse();
        assertThat(ViewOperations.moveOrClone(duplicate, List.of(0, 0), List.of(1), true)).isTrue();
        assertThat(ViewOperations.moveOrClone(duplicate, List.of(0, 0), List.of(1), true)).isFalse();

        ViewDefinition invalid = view();
        assertThat(ViewOperations.moveOrClone(invalid, List.of(), List.of(), true)).isFalse();
        assertThat(ViewOperations.moveOrClone(invalid, List.of(9), List.of(), true)).isFalse();
        assertThat(ViewOperations.moveOrClone(invalid, List.of(0, 0), List.of(0, 0), true)).isFalse();
        assertThat(ViewOperations.moveOrClone(invalid, List.of(0), List.of(), true)).isFalse();
        assertThat(ViewOperations.moveOrClone(invalid, List.of(0, 0), List.of(9), true)).isFalse();
        assertThat(invalid.entries().get(0).children()).hasSize(1);
    }

    @Test
    void addLinkFromPhysicalTreeChecksTargetAndDuplicateSibling() {
        ViewDefinition view = view();

        assertThat(ViewOperations.addLink(view, "new.md", List.of(1))).isTrue();
        assertThat(view.entries().get(1).children()).extracting(ViewEntry::file)
                .containsExactly("b.md", "new.md");
        assertThat(ViewOperations.addLink(view, "new.md", List.of(1))).isFalse();
        assertThat(ViewOperations.addLink(view, "other.md", List.of(0, 0))).isFalse();
        assertThat(ViewOperations.addLink(view, "other.md", List.of(99))).isFalse();
        assertThat(view.entries().get(1).children()).hasSize(2);
    }

    @Test
    void cloneSetCountsOnlyVisibleLinksAcrossAllViews() {
        ViewEntry visible = ViewEntry.file("shared.md", null, true, List.of());
        ViewEntry hidden = ViewEntry.file("hidden.md", null, true, List.of());
        ViewDefinition first = new ViewDefinition("first", "First", Path.of("first.yml"),
                new ArrayList<>(List.of(
                        ViewEntry.node("A", null, true, List.of(), new ArrayList<>(List.of(visible))),
                        ViewEntry.node("B", null, true, List.of(), new ArrayList<>(List.of(visible))),
                        ViewEntry.node("Hidden", null, false, List.of(), new ArrayList<>(List.of(hidden))))));
        ViewDefinition second = new ViewDefinition("second", "Second", Path.of("second.yml"),
                new ArrayList<>(List.of(ViewEntry.file("other.md", null, false, List.of()), hidden)));

        assertThat(ViewOperations.clonedFiles(List.of(first, second)))
                .containsExactly("shared.md");
    }

    @Test
    void copyEntriesDetachesMutableHierarchy() {
        ViewDefinition view = view();
        List<ViewEntry> copy = ViewOperations.copyEntries(view.entries());

        copy.getFirst().children().clear();

        assertThat(view.entries().getFirst().children()).hasSize(1);
        assertThat(copy.getFirst().tags()).isEqualTo(view.entries().getFirst().tags());
    }

    private static ViewDefinition view() {
        ViewEntry source = ViewEntry.file("a.md", null, true, List.of());
        ViewEntry target = ViewEntry.file("b.md", null, true, List.of());
        return new ViewDefinition("view", "View", Path.of("view.yml"), new ArrayList<>(List.of(
                ViewEntry.node("Source", null, true, List.of(), new ArrayList<>(List.of(source))),
                ViewEntry.node("Target", null, true, List.of(), new ArrayList<>(List.of(target))))));
    }
}
