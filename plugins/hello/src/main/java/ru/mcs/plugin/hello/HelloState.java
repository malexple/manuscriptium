package ru.mcs.plugin.hello;

import java.util.LinkedHashMap;
import java.util.Map;
import ru.mcs.spi.service.PersistentStateComponent;

/** Счётчик нажатий, переживающий перезапуск приложения. */
final class HelloState implements PersistentStateComponent {

    static final int MIN_STEP = 1;
    static final int MAX_STEP = 100;

    private volatile int clicks;
    private volatile int step = MIN_STEP;

    int clicks() {
        return clicks;
    }

    int step() {
        return step;
    }

    synchronized void setStep(int value) {
        if (value < MIN_STEP || value > MAX_STEP) {
            throw new IllegalArgumentException("Counter step must be between " + MIN_STEP + " and " + MAX_STEP);
        }
        step = value;
    }

    synchronized void increment() {
        clicks += step;
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("clicks", clicks);
        state.put("step", step);
        return state;
    }

    @Override
    public void loadState(Map<String, Object> state) {
        if (state.get("clicks") instanceof Number n) {
            clicks = n.intValue();
        }
        if (state.get("step") instanceof Number n) {
            int value = n.intValue();
            if (value >= MIN_STEP && value <= MAX_STEP) {
                step = value;
            }
        }
    }
}
