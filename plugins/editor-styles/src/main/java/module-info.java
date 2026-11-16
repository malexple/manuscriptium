module ru.mcs.plugin.editorstyle {
    requires transitive ru.mcs.spi.swing;
    requires java.desktop;

    provides ru.mcs.spi.swing.Plugin with ru.mcs.plugin.editorstyle.EditorStylePlugin;
}
