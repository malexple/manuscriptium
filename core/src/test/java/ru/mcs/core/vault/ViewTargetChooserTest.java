package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ViewTargetChooserTest {

    @Test
    void offersOnlyVisibleValidTargetsForBothCloneAndMove() {
        ViewDefinition view = view();
        String original = view.entries().toString();

        List<ViewTargetChooser.Target> clone = ViewTargetChooser.targets(view, List.of(0, 0), true);
        List<ViewTargetChooser.Target> move = ViewTargetChooser.targets(view, List.of(0, 0), false);

        assertThat(clone).extracting(ViewTargetChooser.Target::path)
                .containsExactly(List.of(), List.of(0, 1), List.of(2));
        assertThat(move).extracting(ViewTargetChooser.Target::path)
                .isEqualTo(clone.stream().map(ViewTargetChooser.Target::path).toList());
        assertThat(clone.getFirst().title()).startsWith("View (");
        assertThat(clone).extracting(ViewTargetChooser.Target::title)
                .contains("Source / Nested", "Empty");
        assertThat(view.entries().toString()).isEqualTo(original);
    }

    @Test
    void rejectsInvalidSourceAndNodeSource() {
        ViewDefinition view = view();

        assertThat(ViewTargetChooser.targets(view, List.of(99), true)).isEmpty();
        assertThat(ViewTargetChooser.targets(view, List.of(0), true)).isEmpty();
        assertThat(ViewTargetChooser.targets(view, List.of(), true)).isEmpty();
    }

    private static ViewDefinition view() {
        return new ViewDefinition("view", "View", Path.of("view.yml"), new ArrayList<>(List.of(
                ViewEntry.node("Source", null, true, List.of(), new ArrayList<>(List.of(
                        ViewEntry.file("a.md", null, true, List.of()),
                        ViewEntry.node("Nested", null, true, List.of(), new ArrayList<>()),
                        ViewEntry.node("Hidden child", null, false, List.of(), new ArrayList<>())))),
                ViewEntry.node("Duplicate", null, true, List.of(), new ArrayList<>(List.of(
                        ViewEntry.file("a.md", null, true, List.of())))),
                ViewEntry.node("Empty", null, true, List.of(), new ArrayList<>()),
                ViewEntry.node("Hidden root", null, false, List.of(), new ArrayList<>(List.of(
                        ViewEntry.node("Hidden descendant", null, true, List.of(), new ArrayList<>())))),
                ViewEntry.file("root.md", null, true, List.of()))));
    }
}
