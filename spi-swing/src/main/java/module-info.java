module ru.mcs.spi.swing {
    requires transitive java.desktop;
    requires transitive ru.mcs.spi;

    exports ru.mcs.spi.swing;
    exports ru.mcs.spi.swing.action;
    exports ru.mcs.spi.swing.editor;
    exports ru.mcs.spi.swing.editor.style;
    exports ru.mcs.spi.swing.settings;
    exports ru.mcs.spi.swing.ui;
}
