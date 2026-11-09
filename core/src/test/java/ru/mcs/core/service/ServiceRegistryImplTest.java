package ru.mcs.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.mcs.spi.service.ServiceRegistry;

class ServiceRegistryImplTest {

    private final ServiceRegistryImpl registry = new ServiceRegistryImpl();

    @Test
    void registerAndLookup() {
        registry.register(String.class, "svc");
        assertThat(registry.get(String.class)).isEqualTo("svc");
        assertThat(registry.find(String.class)).contains("svc");
        assertThat(registry.find(Integer.class)).isEmpty();
    }

    @Test
    void missingServiceFailsWithClearMessage() {
        assertThatThrownBy(() -> registry.get(Integer.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("java.lang.Integer");
    }

    @Test
    void duplicateRegistrationIsRejected() {
        registry.register(String.class, "a");
        assertThatThrownBy(() -> registry.register(String.class, "b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ownerCleanupRemovesOnlyItsServices() {
        ServiceRegistry scoped = registry.scopedFor("p1");
        scoped.register(String.class, "mine");
        registry.register(Integer.class, 42);

        registry.unregisterOwner("p1");

        assertThat(registry.find(String.class)).isEmpty();
        assertThat(registry.find(Integer.class)).contains(42);
    }

    @Test
    void scopedViewSeesOtherPluginsServices() {
        registry.register(Integer.class, 7);
        assertThat(registry.scopedFor("p2").get(Integer.class)).isEqualTo(7);
    }
}
