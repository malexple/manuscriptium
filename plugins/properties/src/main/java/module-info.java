module ru.mcs.plugin.properties {
    requires ru.mcs.spi.swing;

    // Разрешает Icons.load(Class, path) читать SVG из named-модуля плагина.
    opens ru.mcs.plugin.properties.icons;

    provides ru.mcs.spi.swing.Plugin with ru.mcs.plugin.properties.PropertiesPlugin;
}
