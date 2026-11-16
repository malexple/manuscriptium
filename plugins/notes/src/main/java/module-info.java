module ru.mcs.plugin.notes {
    requires ru.mcs.spi.swing;

    // Разрешает Icons.load(Class, path) читать SVG из named-модуля плагина.
    opens ru.mcs.plugin.notes.icons;

    provides ru.mcs.spi.swing.Plugin with ru.mcs.plugin.notes.NotesPlugin;
}
