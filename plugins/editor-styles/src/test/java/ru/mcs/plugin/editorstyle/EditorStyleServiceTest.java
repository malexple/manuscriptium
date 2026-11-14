package ru.mcs.plugin.editorstyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.editor.style.ShellThemeService;

class EditorStyleServiceTest {

    @Test
    void autoThemeFollowsShellLightAndDarkOutsideEdt() throws Exception {
        FakeShellTheme shell = new FakeShellTheme();
        EditorStyleState state = new EditorStyleState();
        DefaultEditorStyleService service = new DefaultEditorStyleService(state, shell, message -> { });
        try {
            service.reload();
            assertThat(service.selectedTheme()).isEqualTo("github");
            shell.setDark(true);
            awaitTheme(service, "night");
            assertThat(service.selectedTheme()).isEqualTo("night");
            assertThat(service.availableThemes()).hasSize(9);
            assertThat(service.diagnostics()).isNotEmpty();
        } finally {
            service.close();
        }
    }

    @Test
    void explicitThemeDoesNotFollowShellMode() throws Exception {
        FakeShellTheme shell = new FakeShellTheme();
        DefaultEditorStyleService service = new DefaultEditorStyleService(new EditorStyleState(), shell, message -> { });
        try {
            service.selectTheme("vue");
            awaitTheme(service, "vue");
            shell.setDark(true);
            assertThat(service.selectedTheme()).isEqualTo("vue");
        } finally {
            service.close();
        }
    }

    private static void awaitTheme(DefaultEditorStyleService service, String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (expected.equals(service.selectedTheme()) && !service.styleSheet().rules().isEmpty()) {
                return;
            }
            Thread.sleep(10);
        }
        assertThat(service.selectedTheme()).isEqualTo(expected);
    }

    private static final class FakeShellTheme implements ShellThemeService {
        private final List<Consumer<Snapshot>> listeners = new ArrayList<>();
        private boolean dark;

        @Override
        public String selectedTheme() {
            return dark ? "dark" : "light";
        }

        @Override
        public boolean isDark() {
            return dark;
        }

        @Override
        public Path directory() {
            return Path.of("build/test-shell-themes");
        }

        @Override
        public void addThemeListener(Consumer<Snapshot> listener) {
            listeners.add(listener);
        }

        @Override
        public void removeThemeListener(Consumer<Snapshot> listener) {
            listeners.remove(listener);
        }

        private void setDark(boolean value) {
            dark = value;
            Snapshot snapshot = new Snapshot(selectedTheme(), dark);
            listeners.forEach(listener -> listener.accept(snapshot));
        }
    }
}
