package ru.mcs.core.project;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ProjectSearchQueryTest {

    @Test
    void normalizesPortableSubtreeAndTrimsText() {
        ProjectSearchQuery query = new ProjectSearchQuery("  Java 21  ", null, "notes\\java/", Map.of(), 100);
        assertThat(query.text()).isEqualTo("Java 21");
        assertThat(query.scope()).isEqualTo(ProjectSearchScope.ALL);
        assertThat(query.subtree()).isEqualTo("notes/java");
        assertThat(query.attributeFilters()).isEmpty();
        assertThat(query.isEmpty()).isFalse();
        assertThat(new ProjectSearchQuery(" ", ProjectSearchScope.ALL, null, Map.of(), 100).isEmpty()).isTrue();
    }

    @Test
    void rejectsUnsafeSubtreesAndUnboundedRequests() {
        assertThatIllegalArgumentException().isThrownBy(() -> query("../outside"));
        assertThatIllegalArgumentException().isThrownBy(() -> query("/absolute"));
        assertThatIllegalArgumentException().isThrownBy(() -> query("C:/absolute"));
        assertThatIllegalArgumentException().isThrownBy(() -> query("notes//nested"));
        assertThatIllegalArgumentException().isThrownBy(() -> query(".manuscriptium/lucene"));
        assertThatIllegalArgumentException().isThrownBy(() -> query(".git"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ProjectSearchQuery("x".repeat(1001), ProjectSearchScope.ALL, "", Map.of(), 100));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ProjectSearchQuery("", ProjectSearchScope.ALL, "", null, 0));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ProjectSearchQuery("", ProjectSearchScope.ALL, "", null, 1001));
    }

    private static ProjectSearchQuery query(String subtree) {
        return new ProjectSearchQuery("", ProjectSearchScope.ALL, subtree, Map.of(), 100);
    }
}
