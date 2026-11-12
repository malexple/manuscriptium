package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhysicalLinkChoosersTest {

    @Test
    void findsVisibleSourcesAcrossViewsAndTargetsWithoutDuplicates() {
        List<ViewDefinition> views = views();
        String original = views.toString();

        List<PhysicalLinkSources.Source> sources = PhysicalLinkSources.find(views, "a.md");
        List<PhysicalLinkTargetChooser.Target> targets = PhysicalLinkTargetChooser.targets(views, "a.md");

        assertThat(sources).extracting(source -> source.view().id()).containsExactly("first", "first");
        assertThat(sources).extracting(PhysicalLinkSources.Source::path)
                .containsExactly(List.of(0, 0), List.of(1, 0));
        assertThat(sources).extracting(PhysicalLinkSources.Source::title)
                .allMatch(title -> title.startsWith("First / "));
        assertThat(targets).extracting(PhysicalLinkTargetChooser.Target::title)
                .anyMatch(title -> title.equals("First / Empty"))
                .anyMatch(title -> title.startsWith("Second ("))
                .anyMatch(title -> title.equals("Second / Target"));
        assertThat(targets).extracting(PhysicalLinkTargetChooser.Target::title)
                .noneMatch(title -> title.contains("Source"))
                .noneMatch(title -> title.contains("Duplicate"))
                .noneMatch(title -> title.contains("Hidden"));

        List<PhysicalLinkTargetChooser.Target> moveTargets = PhysicalLinkTargetChooser.moveTargets(
                views, sources.getFirst());
        assertThat(moveTargets).extracting(PhysicalLinkTargetChooser.Target::title)
                .anyMatch(title -> title.equals("First / Empty"))
                .anyMatch(title -> title.startsWith("Second ("))
                .anyMatch(title -> title.equals("Second / Target"))
                .noneMatch(title -> title.contains("Source"))
                .noneMatch(title -> title.contains("Duplicate"));
        assertThat(views.toString()).isEqualTo(original);
    }

    @Test
    void generatesPortableIdsWithFallbackForNonAsciiNames() {
        assertThat(ViewIds.base(" My  Classification ")).isEqualTo("my-classification");
        assertThat(ViewIds.base("Классификация")).isEqualTo("view");
        assertThat(ViewIds.base("Café / Java 21")).isEqualTo("cafe-java-21");
    }

    private static List<ViewDefinition> views() {
        ViewDefinition first = new ViewDefinition("first", "First", Path.of("first.yml"), new ArrayList<>(List.of(
                ViewEntry.node("Source", null, true, List.of(), new ArrayList<>(List.of(
                        ViewEntry.file("a.md", null, true, List.of())))),
                ViewEntry.node("Duplicate", null, true, List.of(), new ArrayList<>(List.of(
                        ViewEntry.file("a.md", null, true, List.of())))),
                ViewEntry.node("Empty", null, true, List.of(), new ArrayList<>()),
                ViewEntry.node("Hidden", null, false, List.of(), new ArrayList<>(List.of(
                        ViewEntry.file("a.md", null, true, List.of())))))));
        ViewDefinition second = new ViewDefinition("second", "Second", Path.of("second.yml"), new ArrayList<>(List.of(
                ViewEntry.node("Target", null, true, List.of(), new ArrayList<>()))));
        return List.of(first, second);
    }
}
