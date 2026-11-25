package ru.mcs.markdown.editor;

import java.util.Map;
import ru.mcs.spi.service.PersistentStateComponent;

/** Сохраняемые настройки вкладки «Исходник»: пока только показ номеров строк. */
final class MarkdownSourceViewState implements PersistentStateComponent {

    private volatile boolean showLineNumbers = true;

    boolean showLineNumbers() {
        return showLineNumbers;
    }

    void setShowLineNumbers(boolean value) {
        showLineNumbers = value;
    }

    @Override
    public Map<String, Object> getState() {
        return Map.of("showLineNumbers", showLineNumbers);
    }

    @Override
    public void loadState(Map<String, Object> state) {
        if (state.get("showLineNumbers") instanceof Boolean flag) {
            showLineNumbers = flag;
        }
    }
}
