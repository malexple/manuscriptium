package ru.mcs.spi.editor;

import ru.mcs.spi.event.Topic;

public final class EditorTopics {

    public static final Topic<FileEditorListener> EDITOR = Topic.create("mcs.editor", FileEditorListener.class);

    private EditorTopics() {
    }
}
