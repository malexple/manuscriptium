package ru.mcs.markdown.render;

import java.util.List;

/** Имена токенов подсветки Markdown; на них опираются палитра и страница «Цветовая схема». */
public final class MarkdownTokens {

    public static final String HEADING = "md.heading";
    public static final String HEADING_MARKER = "md.heading.marker";
    public static final String BOLD = "md.bold";
    public static final String ITALIC = "md.italic";
    public static final String STRIKE = "md.strike";
    public static final String CODE = "md.code";
    public static final String FENCE = "md.fence";
    public static final String CODE_BLOCK = "md.codeblock";
    public static final String LINK_TEXT = "md.link.text";
    public static final String LINK_URL = "md.link.url";
    public static final String WIKILINK = "md.wikilink";
    public static final String FOOTNOTE = "md.footnote";
    public static final String LIST = "md.list";
    public static final String TASK = "md.task";
    public static final String QUOTE = "md.quote";
    public static final String HR = "md.hr";
    public static final String TABLE = "md.table";
    public static final String HTML = "md.html";
    public static final String COMMENT = "md.comment";
    public static final String FRONT_MATTER = "md.frontmatter";
    public static final String MATH = "md.math";

    public static final List<String> ALL = List.of(HEADING, HEADING_MARKER, BOLD, ITALIC, STRIKE, CODE, FENCE,
            CODE_BLOCK, LINK_TEXT, LINK_URL, WIKILINK, FOOTNOTE, LIST, TASK, QUOTE, HR, TABLE, HTML, COMMENT,
            FRONT_MATTER, MATH);

    private MarkdownTokens() {
    }
}
