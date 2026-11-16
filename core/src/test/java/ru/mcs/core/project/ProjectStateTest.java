package ru.mcs.core.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectStateTest {

    @TempDir
    Path tmp;

    @Test
    void roundTripsLastProjectAndClearsInvalidState() {
        ProjectState state = new ProjectState();
        state.setProject(tmp.resolve("project"));
        Map<String, Object> saved = state.getState();

        ProjectState loaded = new ProjectState();
        loaded.loadState(saved);
        assertThat(loaded.project()).contains(tmp.resolve("project").toAbsolutePath().normalize());

        loaded.loadState(Map.of("last", ""));
        assertThat(loaded.project()).isEmpty();
    }
}
