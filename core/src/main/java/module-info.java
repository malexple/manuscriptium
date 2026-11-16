module ru.mcs.core {
    requires transitive ru.mcs.spi;
    requires java.management;
    requires jdk.management;
    requires java.sql;
    requires com.formdev.flatlaf;
    requires com.formdev.flatlaf.extras;
    requires com.github.weisj.jsvg;
    requires org.yaml.snakeyaml;
    requires org.xerial.sqlitejdbc;
    requires org.apache.lucene.core;

    exports ru.mcs.core;
    exports ru.mcs.core.platform;

    uses ru.mcs.spi.Plugin;
}
