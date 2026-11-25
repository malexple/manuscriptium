package ru.mcs.markdown.editor;

import java.util.LinkedHashMap;
import java.util.Map;
import ru.mcs.spi.service.PersistentStateComponent;

/** Сохраняемые настройки вкладки «Исходник»: номера строк и подсветка синтаксиса. */
final class MarkdownSourceViewState implements PersistentStateComponent {

    private volatile boolean showLineNumbers = true;
    private volatile boolean showSyntaxHighlighting = true;

    boolean showLineNumbers() {
        return showLineNumbers;
    }

    void setShowLineNumbers(boolean value) {
        showLineNumbers = value;
    }

    boolean showSyntaxHighlighting() {
        return showSyntaxHighlighting;
    }

    void setShowSyntaxHighlighting(boolean value) {
        showSyntaxHighlighting = value;
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("showLineNumbers", showLineNumbers);
        state.put("showSyntaxHighlighting", showSyntaxHighlighting);
        return state;
    }

    @Override
    public void loadState(Map<String, Object> state) {
        if (state.get("showLineNumbers") instanceof Boolean flag) {
            showLineNumbers = flag;
        }
        if (state.get("showSyntaxHighlighting") instanceof Boolean flag) {
            showSyntaxHighlighting = flag;
        }
    }
}
