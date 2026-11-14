module ru.mcs.plugin.mdparser {
    requires ru.mcs.spi;
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

    provides ru.mcs.spi.Plugin with ru.mcs.plugin.mdparser.MarkdownParserPlugin;
}
