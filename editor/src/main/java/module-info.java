module ru.mcs.editor {
    requires transitive ru.mcs.spi.swing;
    requires ru.mcs.metadata;

    exports ru.mcs.editor.action;
    exports ru.mcs.editor.draw;
    exports ru.mcs.editor.model;
    exports ru.mcs.editor.mode;
    exports ru.mcs.editor.view;

    provides ru.mcs.spi.swing.editor.FileEditorProvider
            with ru.mcs.editor.text.TextFileEditorProvider;
    provides ru.mcs.metadata.MetadataProvider
            with ru.mcs.editor.action.EditorMetadataProvider;
}
