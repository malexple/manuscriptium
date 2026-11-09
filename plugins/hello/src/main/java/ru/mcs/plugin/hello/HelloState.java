package ru.mcs.plugin.hello;

import java.util.LinkedHashMap;
import java.util.Map;
import ru.mcs.spi.service.PersistentStateComponent;

/** Счётчик нажатий, переживающий перезапуск приложения. */
final class HelloState implements PersistentStateComponent {

    private volatile int clicks;

    int clicks() {
        return clicks;
    }

    synchronized void increment() {
        clicks++;
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("clicks", clicks);
        return state;
    }

    @Override
    public void loadState(Map<String, Object> state) {
        if (state.get("clicks") instanceof Number n) {
            clicks = n.intValue();
        }
    }
}
