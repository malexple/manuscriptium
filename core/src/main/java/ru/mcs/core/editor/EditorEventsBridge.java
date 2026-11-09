package ru.mcs.core.editor;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import ru.mcs.spi.editor.EditorTopics;
import ru.mcs.spi.editor.FileEditorListener;
import ru.mcs.spi.event.MessageBus;

/** Превращает события модели редактора в сообщения шины (тема {@link EditorTopics#EDITOR}). */
public final class EditorEventsBridge {

    private EditorEventsBridge() {
    }

    public static void install(FileEditorManagerImpl editors, MessageBus bus) {
        FileEditorListener publisher = bus.publisher(EditorTopics.EDITOR);
        Set<Path> known = new HashSet<>();
        editors.addListener(new FileEditorManagerImpl.Listener() {
            @Override
            public void tabsChanged() {
                Set<Path> now = new LinkedHashSet<>(editors.getOpenFiles());
                for (Path p : now) {
                    if (!known.contains(p)) {
                        publisher.fileOpened(p);
                    }
                }
                for (Path p : new HashSet<>(known)) {
                    if (!now.contains(p)) {
                        publisher.fileClosed(p);
                    }
                }
                known.clear();
                known.addAll(now);
            }

            @Override
            public void selectionChanged() {
                publisher.activeFileChanged(editors.getSelectedFile().orElse(null));
            }
        });
    }
}
