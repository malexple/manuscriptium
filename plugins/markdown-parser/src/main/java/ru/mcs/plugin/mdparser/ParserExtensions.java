package ru.mcs.plugin.mdparser;

import com.vladsch.flexmark.ext.footnotes.FootnoteExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension;
import com.vladsch.flexmark.util.misc.Extension;
import java.util.List;

/** Единый набор расширений E3; math и callout добавляют source-spans поверх этого AST. */
public final class ParserExtensions {

    private ParserExtensions() {
    }

    public static List<Extension> all() {
        return List.of(
                YamlFrontMatterExtension.create(),
                WikiLinkExtension.create(),
                FootnoteExtension.create(),
                TaskListExtension.create(),
                TablesExtension.create(),
                StrikethroughExtension.create());
    }
}
