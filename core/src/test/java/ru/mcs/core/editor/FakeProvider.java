package ru.mcs.core.editor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import ru.mcs.spi.editor.FileEditorProvider;

final class FakeProvider implements FileEditorProvider {

    final String id;
    final int priority;
    final Predicate<Path> accepts;
    final List<FakeEditor> created = new ArrayList<>();

    FakeProvider(String id, int priority, Predicate<Path> accepts) {
        this.id = id;
        this.priority = priority;
        this.accepts = accepts;
    }

    static FakeProvider any(String id) {
        return new FakeProvider(id, 0, p -> true);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean accept(Path file) {
        return accepts.test(file);
    }

    @Override
    public FakeEditor createEditor(Path file) {
        FakeEditor editor = new FakeEditor(file);
        created.add(editor);
        return editor;
    }
}
