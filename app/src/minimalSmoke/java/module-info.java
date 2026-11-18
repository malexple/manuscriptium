// Тестовая компоновка того же app-модуля: основной ManuscriptiumApp и markdown сюда не входят.
module ru.mcs.app {
    requires ru.mcs.core;
    requires ru.mcs.editor;

    uses ru.mcs.spi.swing.editor.FileEditorProvider;
}
