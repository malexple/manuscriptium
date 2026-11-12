module ru.mcs.core {
    requires transitive ru.mcs.spi;
    requires java.management;
    requires java.sql;
    requires com.formdev.flatlaf;
    requires com.formdev.flatlaf.extras;
    requires com.github.weisj.jsvg;
    requires org.yaml.snakeyaml;
    requires org.xerial.sqlitejdbc;

    exports ru.mcs.core;

    uses ru.mcs.spi.Plugin;
}
