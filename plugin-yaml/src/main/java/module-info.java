module ru.mcs.plugin.yaml {
    requires ru.mcs.spi.swing;
    requires ru.mcs.editor;
    requires org.yaml.snakeyaml;

    // Хост читает ResourceBundle из named-модуля плагина.
    opens ru.mcs.yaml.messages;

    provides ru.mcs.spi.swing.Plugin with ru.mcs.yaml.YamlPlugin;
}
