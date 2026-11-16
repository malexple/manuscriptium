package ru.mcs.core.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.core.state.StateManagerImpl;
import ru.mcs.spi.service.Scope;

class ProjectStateMigrationTest {

    @TempDir
    Path tmp;

    @Test
    void readsLegacyStateAndSavesItUnderProjectIdWithoutDeletingLegacyFile() throws Exception {
        Path appDir = tmp.resolve("home/state");
        Path legacyProject = tmp.resolve("legacy-project").toAbsolutePath().normalize();
        Files.createDirectories(appDir);
        Path legacyFile = appDir.resolve("core.vault.yml");
        String legacyText = "last: '" + legacyProject.toString().replace("'", "''") + "'\n";
        Files.writeString(legacyFile, legacyText);
        ProjectState state = new ProjectState();
        StateManagerImpl manager = new StateManagerImpl(appDir, null, true);

        manager.register("core", "core.project", Scope.APPLICATION, state, "core.vault");

        assertThat(state.project()).contains(legacyProject);
        manager.saveChanged();
        assertThat(appDir.resolve("core.project.yml")).exists();
        assertThat(Files.readString(legacyFile)).isEqualTo(legacyText);

        ProjectState reloaded = new ProjectState();
        StateManagerImpl secondManager = new StateManagerImpl(appDir, null, true);
        secondManager.register("core", "core.project", Scope.APPLICATION, reloaded, "core.vault");
        assertThat(reloaded.project()).contains(legacyProject);
    }

    @Test
    void existingProjectStateTakesPrecedenceOverLegacyState() throws Exception {
        Path appDir = tmp.resolve("home/state");
        Path currentProject = tmp.resolve("current").toAbsolutePath().normalize();
        Path legacyProject = tmp.resolve("legacy").toAbsolutePath().normalize();
        Files.createDirectories(appDir);
        Files.writeString(appDir.resolve("core.project.yml"), yamlLast(currentProject));
        Files.writeString(appDir.resolve("core.vault.yml"), yamlLast(legacyProject));
        ProjectState state = new ProjectState();
        StateManagerImpl manager = new StateManagerImpl(appDir, null, true);

        manager.register("core", "core.project", Scope.APPLICATION, state, "core.vault");

        assertThat(state.project()).contains(currentProject);
    }

    private static String yamlLast(Path project) {
        return "last: '" + project.toString().replace("'", "''") + "'\n";
    }
}
