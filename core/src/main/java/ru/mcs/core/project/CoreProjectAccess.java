package ru.mcs.core.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import ru.mcs.spi.project.ProjectAccess;
import ru.mcs.spi.project.ViewDefinition;

/** Публикует неизменяемые снимки Project, не раскрывая core-реализацию языковым модулям. */
public final class CoreProjectAccess implements ProjectAccess {

    private record State(Path root, List<Path> files, Map<String, ViewDefinition> views, long revision) {
        private static State empty() {
            return new State(null, List.of(), Map.of(), 0);
        }
    }

    private volatile State state = State.empty();

    @Override
    public Optional<Path> root() {
        return Optional.ofNullable(state.root());
    }

    @Override
    public List<Path> files() {
        return state.files();
    }

    @Override
    public Optional<ViewDefinition> view(String id) {
        return Optional.ofNullable(state.views().get(id));
    }

    @Override
    public long revision() {
        return state.revision();
    }

    public void update(ProjectSnapshot snapshot) {
        State previous = state;
        List<Path> files = snapshot.notes().stream().map(ProjectNote::file).toList();
        Map<String, ViewDefinition> views = snapshot.views().stream()
                .collect(Collectors.toUnmodifiableMap(ViewDefinition::id, Function.identity()));
        state = new State(previous.root(), List.copyOf(files), views, previous.revision() + 1);
    }

    public void setRoot(Path root) {
        State previous = state;
        state = new State(root.toAbsolutePath().normalize(), previous.files(), previous.views(),
                previous.revision() + 1);
    }

    public void clear() {
        State previous = state;
        state = new State(null, List.of(), Map.of(), previous.revision() + 1);
    }
}
