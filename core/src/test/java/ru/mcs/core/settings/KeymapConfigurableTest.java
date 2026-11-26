package ru.mcs.core.settings;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class KeymapConfigurableTest {

    @Test
    void conflictsAreRejectedAndChangesReachTheServiceOnApply() throws Exception {
        KeymapServiceImpl service = new KeymapServiceImpl();
        service.registerScope("lang", KeymapServiceImplTest.scope());
        KeymapConfigurable configurable = new KeymapConfigurable(service, "lang");

        SwingUtilities.invokeAndWait(() -> {
            assertThat(configurable.createComponent()).isNotNull();
            assertThat(configurable.isModified()).isFalse();

            assertThat(configurable.conflict("s", "a", "shortcut K")).isEqualTo("Action B");
            assertThat(configurable.setShortcut("s", "a", "shortcut K")).isFalse();
            assertThat(configurable.setShortcut("s", "a", "garbage key")).isFalse();
            assertThat(configurable.isModified()).isFalse();

            assertThat(configurable.setShortcut("s", "a", "alt 1")).isTrue();
            assertThat(configurable.shortcutOf("s", "a")).isEqualTo("alt 1");
            assertThat(configurable.isModified()).isTrue();
            assertThat(service.shortcut("s", "a")).isEqualTo("shortcut L");

            configurable.apply();
            assertThat(service.shortcut("s", "a")).isEqualTo("alt 1");
            assertThat(configurable.isModified()).isFalse();
        });
    }

    @Test
    void freedShortcutCanBeReusedAndDefaultsAreNotOverrides() throws Exception {
        KeymapServiceImpl service = new KeymapServiceImpl();
        service.registerScope("lang", KeymapServiceImplTest.scope());
        KeymapConfigurable configurable = new KeymapConfigurable(service, "lang");

        SwingUtilities.invokeAndWait(() -> {
            configurable.createComponent();
            assertThat(configurable.setShortcut("s", "b", "")).isTrue();
            assertThat(configurable.setShortcut("s", "a", "shortcut K")).isTrue();
            assertThat(configurable.setShortcut("s", "a", "ctrl L")).isTrue();
            assertThat(configurable.isModified()).isTrue();

            configurable.resetAll();
            assertThat(configurable.isModified()).isFalse();
            assertThat(configurable.shortcutOf("s", "b")).isEqualTo("shortcut K");

            assertThat(configurable.setShortcut("s", "c", "alt 2")).isTrue();
            configurable.apply();
            assertThat(service.shortcut("s", "c")).isEqualTo("alt 2");
            configurable.resetShortcut("s", "c");
            assertThat(configurable.shortcutOf("s", "c")).isEmpty();
            configurable.reset();
            assertThat(configurable.isModified()).isFalse();
            assertThat(configurable.shortcutOf("s", "c")).isEqualTo("alt 2");
        });
    }

    @Test
    void emptyServiceStillProducesAPage() throws Exception {
        KeymapConfigurable configurable = new KeymapConfigurable(new KeymapServiceImpl(), "none");

        SwingUtilities.invokeAndWait(() -> {
            assertThat(configurable.createComponent()).isNotNull();
            assertThat(configurable.isModified()).isFalse();
        });
    }
}
