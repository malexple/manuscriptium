package ru.mcs.yaml;

import java.util.LinkedHashMap;
import java.util.Map;
import ru.mcs.spi.service.PersistentStateComponent;

/** Editor → General → YAML. */
final class YamlSettings implements PersistentStateComponent {

    private volatile boolean lineNumbers = true;
    private volatile boolean highlighting = true;
    private volatile boolean validate = true;
    private volatile boolean strictKeys = true;

    boolean lineNumbers() {
        return lineNumbers;
    }

    boolean highlighting() {
        return highlighting;
    }

    boolean validate() {
        return validate;
    }

    boolean strictKeys() {
        return strictKeys;
    }

    void set(boolean lineNumbers, boolean highlighting, boolean validate, boolean strictKeys) {
        this.lineNumbers = lineNumbers;
        this.highlighting = highlighting;
        this.validate = validate;
        this.strictKeys = strictKeys;
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("lineNumbers", lineNumbers);
        state.put("highlighting", highlighting);
        state.put("validate", validate);
        state.put("strictKeys", strictKeys);
        return state;
    }

    @Override
    public void loadState(Map<String, Object> state) {
        set(flag(state, "lineNumbers", lineNumbers), flag(state, "highlighting", highlighting),
                flag(state, "validate", validate), flag(state, "strictKeys", strictKeys));
    }

    private static boolean flag(Map<String, Object> state, String key, boolean fallback) {
        return state.get(key) instanceof Boolean value ? value : fallback;
    }
}
