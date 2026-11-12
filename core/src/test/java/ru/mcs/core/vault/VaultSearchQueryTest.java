package ru.mcs.core.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class VaultSearchQueryTest {

    @Test
    void normalizesPortableSubtreeAndTrimsText() {
        VaultSearchQuery query = new VaultSearchQuery("  Java 21  ", null, "notes\\java/", null, 100);
        assertThat(query.text()).isEqualTo("Java 21");
        assertThat(query.scope()).isEqualTo(VaultSearchScope.ALL);
        assertThat(query.subtree()).isEqualTo("notes/java");
        assertThat(query.draft()).isNull();
        assertThat(query.isEmpty()).isFalse();
        assertThat(new VaultSearchQuery(" ", VaultSearchScope.ALL, null, null, 100).isEmpty()).isTrue();
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
                () -> new VaultSearchQuery("x".repeat(1001), VaultSearchScope.ALL, "", null, 100));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new VaultSearchQuery("", VaultSearchScope.ALL, "", null, 0));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new VaultSearchQuery("", VaultSearchScope.ALL, "", null, 1001));
    }

    private static VaultSearchQuery query(String subtree) {
        return new VaultSearchQuery("", VaultSearchScope.ALL, subtree, null, 100);
    }
}
