package ru.mcs.core.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.core.bus.MessageBusImpl;
import ru.mcs.spi.editor.EditorTopics;
import ru.mcs.spi.editor.FileEditorListener;

class EditorEventsBridgeTest {

    private static Path path(String name) {
        return Path.of(name).toAbsolutePath().normalize();
    }

    @Test
    void editorChangesBecomeBusMessages() {
        FileEditorManagerImpl editors = new FileEditorManagerImpl();
        editors.registerProvider(FakeProvider.any("fake"));
        MessageBusImpl bus = new MessageBusImpl();
        EditorEventsBridge.install(editors, bus);

        List<String> events = new ArrayList<>();
        bus.subscribe(EditorTopics.EDITOR, new FileEditorListener() {
            @Override
            public void fileOpened(Path file) {
                events.add("opened:" + file.getFileName());
            }

            @Override
            public void fileClosed(Path file) {
                events.add("closed:" + file.getFileName());
            }

            @Override
            public void activeFileChanged(Path file) {
                events.add("active:" + (file == null ? "null" : file.getFileName()));
            }
        });

        Path a = path("a.txt");
        Path b = path("b.txt");
        editors.openFile(a);
        editors.openFile(b);
        editors.closeFile(a);
        editors.closeFile(b);

        assertThat(events).containsSubsequence("opened:a.txt", "active:a.txt", "opened:b.txt", "active:b.txt",
                "closed:a.txt", "closed:b.txt", "active:null");
    }
}
