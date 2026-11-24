package ru.mcs.spi.project;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class SearchQueryTest {

    @Test
    void normalizesPortableSubtreeAndTrimsText() {
        SearchQuery query = new SearchQuery("  Java 21  ", null, "notes\\java/", Map.of(), 100);
        assertThat(query.text()).isEqualTo("Java 21");
        assertThat(query.scope()).isEqualTo(SearchScope.ALL);
        assertThat(query.subtree()).isEqualTo("notes/java");
        assertThat(query.attributeFilters()).isEmpty();
        assertThat(query.isEmpty()).isFalse();
        assertThat(new SearchQuery(" ", SearchScope.ALL, null, Map.of(), 100).isEmpty()).isTrue();
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
                () -> new SearchQuery("x".repeat(1001), SearchScope.ALL, "", Map.of(), 100));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new SearchQuery("", SearchScope.ALL, "", null, 0));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new SearchQuery("", SearchScope.ALL, "", null, 1001));
    }

    private static SearchQuery query(String subtree) {
        return new SearchQuery("", SearchScope.ALL, subtree, Map.of(), 100);
    }
}
