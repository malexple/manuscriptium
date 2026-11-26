package ru.mcs.yaml;

import java.util.LinkedHashMap;
import java.util.Map;
import ru.mcs.spi.service.PersistentStateComponent;

/** Editor → Code Style → YAML. */
final class YamlCodeStyleState implements PersistentStateComponent {

    private volatile YamlStyle style = YamlStyle.defaults();

    YamlStyle style() {
        return style;
    }

    void setStyle(YamlStyle value) {
        style = value;
    }

    @Override
    public Map<String, Object> getState() {
        YamlStyle current = style;
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("indentSize", current.indentSize());
        state.put("tabsToSpaces", current.tabsToSpaces());
        state.put("trimTrailingWhitespace", current.trimTrailingWhitespace());
        state.put("finalNewline", current.finalNewline());
        state.put("maxBlankLines", current.maxBlankLines());
        return state;
    }

    @Override
    public void loadState(Map<String, Object> state) {
        YamlStyle base = YamlStyle.defaults();
        style = new YamlStyle(number(state, "indentSize", base.indentSize()),
                flag(state, "tabsToSpaces", base.tabsToSpaces()),
                flag(state, "trimTrailingWhitespace", base.trimTrailingWhitespace()),
                flag(state, "finalNewline", base.finalNewline()),
                number(state, "maxBlankLines", base.maxBlankLines()));
    }

    private static int number(Map<String, Object> state, String key, int fallback) {
        return state.get(key) instanceof Number value ? value.intValue() : fallback;
    }

    private static boolean flag(Map<String, Object> state, String key, boolean fallback) {
        return state.get(key) instanceof Boolean value ? value : fallback;
    }
}
