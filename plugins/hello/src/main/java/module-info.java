module ru.mcs.plugin.hello {
    requires ru.mcs.spi.swing;

    // Разрешает Icons.load(Class, path) читать SVG из named-модуля плагина.
    opens ru.mcs.plugin.hello.icons;

    provides ru.mcs.spi.swing.Plugin with ru.mcs.plugin.hello.HelloPlugin;
}
