module ru.mcs.core {
    uses ru.mcs.spi.swing.editor.FileEditorProvider;
    requires transitive ru.mcs.spi.swing;
    requires ru.mcs.metadata;
    requires java.management;
    requires jdk.management;
    requires java.sql;
    requires com.formdev.flatlaf;
    requires com.formdev.flatlaf.extras;
    requires com.github.weisj.jsvg;
    requires org.yaml.snakeyaml;
    requires org.xerial.sqlitejdbc;

    exports ru.mcs.core;
    exports ru.mcs.core.platform;

    uses ru.mcs.spi.swing.Plugin;
    uses ru.mcs.metadata.MetadataProvider;

    provides ru.mcs.metadata.MetadataProvider
            with ru.mcs.core.action.CoreMetadataProvider;

    // ResourceBundle metadata-provider читает properties из named module.
    opens ru.mcs.core.i18n;
}
