package ru.mcs.markdown.widget;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Горизонтальное смещение таблиц по началу блока: виджеты пересоздаются при раскладке, смещение остаётся. */
public final class TableScrollState {

    private final Map<Integer, Integer> offsets = new ConcurrentHashMap<>();

    public int get(int tableStart) {
        return offsets.getOrDefault(tableStart, 0);
    }

    public void set(int tableStart, int offset) {
        if (offset <= 0) {
            offsets.remove(tableStart);
        } else {
            offsets.put(tableStart, offset);
        }
    }

    public void clear() {
        offsets.clear();
    }
}
