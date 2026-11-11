module ru.mcs.core {
    requires transitive ru.mcs.spi;
    requires java.management;
    requires com.formdev.flatlaf;
    requires com.formdev.flatlaf.extras;
    requires com.github.weisj.jsvg;
    requires org.yaml.snakeyaml;

    exports ru.mcs.core;

    uses ru.mcs.spi.Plugin;
}
