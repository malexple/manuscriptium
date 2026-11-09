package ru.mcs.core.settings;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import ru.mcs.spi.service.PersistentStateComponent;

/** Глобальные настройки приложения (id core.settings). Пока — только язык интерфейса. */
public final class AppSettings implements PersistentStateComponent {

    public static final String AUTO = "auto";
    public static final String EN = "en";
    public static final String RU = "ru";

    private static final Locale RU_LOCALE = Locale.of("ru");

    private volatile String language = AUTO;

    public String language() {
        return language;
    }

    public void setLanguage(String value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Unknown language: " + value);
        }
        this.language = value;
    }

    /** Язык интерфейса: поддерживаются английский и русский; «auto» следует языку системы. */
    public Locale resolveLocale() {
        return resolveLocale(language, Locale.getDefault());
    }

    static Locale resolveLocale(String language, Locale system) {
        return switch (language) {
            case RU -> RU_LOCALE;
            case EN -> Locale.ENGLISH;
            default -> "ru".equals(system.getLanguage()) ? RU_LOCALE : Locale.ENGLISH;
        };
    }

    private static boolean isValid(String value) {
        return AUTO.equals(value) || EN.equals(value) || RU.equals(value);
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("language", language);
        return state;
    }

    @Override
    public void loadState(Map<String, Object> state) {
        if (state.get("language") instanceof String s && isValid(s)) {
            language = s;
        }
    }
}
