module ru.mcs.core {
    requires transitive ru.mcs.spi;
    requires com.formdev.flatlaf;
    requires org.yaml.snakeyaml;

    exports ru.mcs.core;

    uses ru.mcs.spi.Plugin;
}
