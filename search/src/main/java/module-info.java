module ru.mcs.search {
    requires transitive ru.mcs.spi.swing;
    requires ru.mcs.metadata;
    requires com.formdev.flatlaf;
    requires com.formdev.flatlaf.extras;
    requires org.apache.lucene.core;

    exports ru.mcs.search;

    provides ru.mcs.metadata.MetadataProvider
            with ru.mcs.search.SearchMetadataProvider;

    // SVG-иконки и ResourceBundle читаются через Class/Module из других модулей.
    opens ru.mcs.search.icons;
    opens ru.mcs.search.messages;
}
