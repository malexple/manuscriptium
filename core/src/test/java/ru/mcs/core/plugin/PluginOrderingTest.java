package ru.mcs.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PluginOrderingTest {

    private static PluginDescriptor p(String id, String... deps) {
        return new PluginDescriptor(id, id, "1", "0.1", id, List.of(deps), List.of(), Path.of("/p"));
    }

    @Test
    void dependenciesGoFirst() {
        var r = PluginOrdering.sort(List.of(p("c", "b"), p("b", "a"), p("a")));
        assertThat(r.ordered()).extracting(PluginDescriptor::id).containsExactly("a", "b", "c");
        assertThat(r.errors()).isEmpty();
    }

    @Test
    void builtInDependencyIsSatisfiedWithoutAnExternalDescriptor() {
        var r = PluginOrdering.sort(List.of(p("consumer", "markdown")), Set.of("markdown"));

        assertThat(r.ordered()).extracting(PluginDescriptor::id).containsExactly("consumer");
        assertThat(r.errors()).isEmpty();
    }

    @Test
    void sameBuiltInIdIsNotSatisfiedWhenItWasNotProvided() {
        var r = PluginOrdering.sort(List.of(p("consumer", "markdown")), Set.of());

        assertThat(r.ordered()).isEmpty();
        assertThat(r.errors().get("consumer")).contains("missing dependency").contains("markdown");
    }

    @Test
    void externalPluginCannotReplaceBuiltInId() {
        var r = PluginOrdering.sort(List.of(p("markdown"), p("ok")), Set.of("markdown"));

        assertThat(r.ordered()).extracting(PluginDescriptor::id).containsExactly("ok");
        assertThat(r.errors().get("markdown")).contains("conflicts with built-in module");
    }

    @Test
    void missingDependencyIsReportedAndPropagates() {
        var r = PluginOrdering.sort(List.of(p("b", "a"), p("c", "b"), p("ok")));
        assertThat(r.ordered()).extracting(PluginDescriptor::id).containsExactly("ok");
        assertThat(r.errors()).containsKeys("b", "c");
        assertThat(r.errors().get("b")).contains("missing dependency");
    }

    @Test
    void cycleIsDetected() {
        var r = PluginOrdering.sort(List.of(p("a", "b"), p("b", "a")));
        assertThat(r.ordered()).isEmpty();
        assertThat(r.errors()).containsKeys("a", "b");
    }

    @Test
    void duplicateIdsAndTheirDependentsAreRejected() {
        var r = PluginOrdering.sort(List.of(p("a"), p("a"), p("b", "a"), p("ok")));

        assertThat(r.ordered()).extracting(PluginDescriptor::id).containsExactly("ok");
        assertThat(r.errors()).containsKeys("a", "b");
        assertThat(r.errors().get("a")).contains("duplicate plugin id");
        assertThat(r.errors().get("b")).contains("dependency failed: a");
    }
}
