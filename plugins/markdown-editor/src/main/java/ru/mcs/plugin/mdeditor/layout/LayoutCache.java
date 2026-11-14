package ru.mcs.plugin.mdeditor.layout;

/** Кэш последней раскладки: paint никогда не пересчитывает документ без изменения входных параметров. */
public final class LayoutCache {

    private LayoutResult result;
    private int activeBlockStart = Integer.MIN_VALUE;
    private long layoutCount;

    public LayoutResult get(long version, int width, int activeBlockStart) {
        return result != null && result.documentVersion() == version && result.viewportWidth() == width
                && this.activeBlockStart == activeBlockStart ? result : null;
    }

    public void put(LayoutResult result, int activeBlockStart) {
        this.result = result;
        this.activeBlockStart = activeBlockStart;
        layoutCount++;
    }

    public void clear() {
        result = null;
        activeBlockStart = Integer.MIN_VALUE;
    }

    public long layoutCount() {
        return layoutCount;
    }
}
