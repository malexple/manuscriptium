module ru.mcs.plugin.editorstyle {
    requires transitive ru.mcs.spi;
    requires java.desktop;

    provides ru.mcs.spi.Plugin with ru.mcs.plugin.editorstyle.EditorStylePlugin;
}
