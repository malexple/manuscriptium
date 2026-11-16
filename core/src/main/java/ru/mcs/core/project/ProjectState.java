package ru.mcs.core.project;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import ru.mcs.spi.service.PersistentStateComponent;

/** Последний открытый Project — глобальная настройка; сессия и прочее Project-состояние хранится уже внутри него. */
public final class ProjectState implements PersistentStateComponent {

    private Path project;

    public Optional<Path> project() {
        return Optional.ofNullable(project);
    }

    public void setProject(Path value) {
        project = value == null ? null : ProjectPaths.normalize(value);
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        if (project != null) {
            state.put("last", project.toString());
        }
        return state;
    }

    @Override
    public void loadState(Map<String, Object> state) {
        project = null;
        if (state.get("last") instanceof String value && !value.isBlank()) {
            try {
                project = ProjectPaths.normalize(Path.of(value));
            } catch (InvalidPathException ignored) {
                // Повреждённая запись не мешает запуску; пользователь выберет Project заново.
            }
        }
    }
}
