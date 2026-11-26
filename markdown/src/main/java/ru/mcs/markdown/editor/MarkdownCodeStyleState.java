package ru.mcs.markdown.editor;

import java.util.LinkedHashMap;
import java.util.Map;
import ru.mcs.markdown.format.CodeStyle;
import ru.mcs.spi.service.PersistentStateComponent;

/** Сохраняемые параметры «Стиль кода → Markdown». */
final class MarkdownCodeStyleState implements PersistentStateComponent {

    private volatile CodeStyle style = CodeStyle.defaults();

    CodeStyle style() {
        return style;
    }

    void setStyle(CodeStyle value) {
        style = value;
    }

    @Override
    public Map<String, Object> getState() {
        CodeStyle current = style;
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("hardWrap", current.hardWrap());
        state.put("keepLineBreaks", current.keepLineBreaks());
        state.put("wrapQuotes", current.wrapQuotes());
        state.put("formatTables", current.formatTables());
        state.put("maxBlankLines", current.maxBlankLines());
        state.put("blankLinesAround", current.blankLinesAround());
        return state;
    }

    @Override
    public void loadState(Map<String, Object> state) {
        CodeStyle base = CodeStyle.defaults();
        style = new CodeStyle(
                number(state, "hardWrap", base.hardWrap()),
                flag(state, "keepLineBreaks", base.keepLineBreaks()),
                flag(state, "wrapQuotes", base.wrapQuotes()),
                flag(state, "formatTables", base.formatTables()),
                number(state, "maxBlankLines", base.maxBlankLines()),
                number(state, "blankLinesAround", base.blankLinesAround()));
    }

    private static int number(Map<String, Object> state, String key, int fallback) {
        return state.get(key) instanceof Number value ? value.intValue() : fallback;
    }

    private static boolean flag(Map<String, Object> state, String key, boolean fallback) {
        return state.get(key) instanceof Boolean value ? value : fallback;
    }
}
