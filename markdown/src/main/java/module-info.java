module ru.mcs.markdown {
    requires transitive ru.mcs.spi.swing;
    requires ru.mcs.editor;

    requires com.formdev.flatlaf;
    requires com.formdev.flatlaf.extras;
    requires jlatexmath;
    requires org.yaml.snakeyaml;
    requires flexmark;
    requires flexmark.util.ast;
    requires flexmark.util.misc;
    requires flexmark.util.sequence;
    requires flexmark.ext.footnotes;
    requires flexmark.ext.gfm.strikethrough;
    requires flexmark.ext.gfm.tasklist;
    requires flexmark.ext.tables;
    requires flexmark.ext.wikilink;
    requires flexmark.ext.yaml.front.matter;

    exports ru.mcs.markdown.api;

    // Ресурсы SVG доступны Swing SVG loader; implementation packages не экспортируются.
    opens ru.mcs.markdown.editor.icons;
    opens ru.mcs.markdown.properties.icons;
    opens ru.mcs.markdown.notes.icons;
    opens ru.mcs.markdown.messages;
}
