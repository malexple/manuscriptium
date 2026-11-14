package ru.mcs.plugin.mdeditor.layout;

import java.util.List;

/** Одна визуальная строка внутри BlockBox. */
public final class LineBox {

    private final List<InlineRun> runs;
    private final int sourceStart;
    private final int sourceEnd;
    private int x;
    private int y;
    private int height;
    private int ascent;

    LineBox(List<InlineRun> runs, int emptySourceOffset) {
        this.runs = List.copyOf(runs);
        sourceStart = runs.isEmpty() ? emptySourceOffset : runs.getFirst().sourceStart();
        sourceEnd = runs.isEmpty() ? emptySourceOffset : runs.getLast().sourceEnd();
    }

    void place(int x, int y, int height, int ascent) {
        this.x = x;
        this.y = y;
        this.height = height;
        this.ascent = ascent;
        int runX = x;
        for (InlineRun run : runs) {
            run.place(runX, y + ascent);
            runX += Math.round(run.advance());
        }
    }

    public List<InlineRun> runs() {
        return runs;
    }

    public int sourceStart() {
        return sourceStart;
    }

    public int sourceEnd() {
        return sourceEnd;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int height() {
        return height;
    }

    public int ascent() {
        return ascent;
    }

    public int endX() {
        return runs.isEmpty() ? x : runs.getLast().x() + Math.round(runs.getLast().advance());
    }
}
