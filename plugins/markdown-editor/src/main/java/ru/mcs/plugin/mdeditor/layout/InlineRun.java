package ru.mcs.plugin.mdeditor.layout;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.font.TextHitInfo;
import java.awt.font.TextLayout;
import java.util.Arrays;
import ru.mcs.spi.editor.style.ComputedStyle;

/** Видимый фрагмент строки и отображение каждой его границы в UTF-16 offsets исходника. */
public final class InlineRun {

    private final String text;
    private final int[] sourceOffsets;
    private final TextLayout layout;
    private final Font font;
    private final ComputedStyle style;
    private final String linkTarget;
    private final int taskOffset;
    private final boolean taskChecked;
    private int x;
    private int baseline;

    InlineRun(String text, int[] sourceOffsets, TextLayout layout, Font font, ComputedStyle style,
              String linkTarget, int taskOffset, boolean taskChecked) {
        this.text = text;
        this.sourceOffsets = Arrays.copyOf(sourceOffsets, sourceOffsets.length);
        this.layout = layout;
        this.font = font;
        this.style = style;
        this.linkTarget = linkTarget;
        this.taskOffset = taskOffset;
        this.taskChecked = taskChecked;
        if (text.length() + 1 != sourceOffsets.length) {
            throw new IllegalArgumentException("Every visible UTF-16 boundary needs a source offset");
        }
    }

    void place(int x, int baseline) {
        this.x = x;
        this.baseline = baseline;
    }

    public String text() {
        return text;
    }

    public TextLayout textLayout() {
        return layout;
    }

    public Font font() {
        return font;
    }

    public ComputedStyle style() {
        return style;
    }

    public String linkTarget() {
        return linkTarget;
    }

    public int taskOffset() {
        return taskOffset;
    }

    public boolean taskChecked() {
        return taskChecked;
    }

    public int x() {
        return x;
    }

    public int baseline() {
        return baseline;
    }

    public float advance() {
        return layout.getAdvance();
    }

    public int sourceStart() {
        return sourceOffsets[0];
    }

    public int sourceEnd() {
        return sourceOffsets[sourceOffsets.length - 1];
    }

    public int sourceOffset(int visibleIndex) {
        int safe = Math.max(0, Math.min(visibleIndex, text.length()));
        return sourceOffsets[safe];
    }

    public int visibleIndex(int sourceOffset) {
        if (sourceOffset <= sourceOffsets[0]) {
            return 0;
        }
        int last = sourceOffsets.length - 1;
        if (sourceOffset >= sourceOffsets[last]) {
            return last;
        }
        int nearest = 0;
        int distance = Integer.MAX_VALUE;
        for (int i = 0; i <= last; i++) {
            int candidate = Math.abs(sourceOffsets[i] - sourceOffset);
            if (candidate < distance) {
                nearest = i;
                distance = candidate;
            }
        }
        return nearest;
    }

    public int hitOffset(float localX, float localY) {
        TextHitInfo hit = layout.hitTestChar(localX, localY);
        return sourceOffset(hit.getInsertionIndex());
    }

    public float caretX(int sourceOffset) {
        int visible = visibleIndex(sourceOffset);
        TextHitInfo hit = visible == text.length() && !text.isEmpty()
                ? TextHitInfo.trailing(visible - 1) : TextHitInfo.leading(visible);
        return x + layout.getCaretInfo(hit)[0];
    }

    public Rectangle bounds(int lineY, int lineHeight) {
        return new Rectangle(x, lineY, Math.max(1, Math.round(advance())), lineHeight);
    }

    public Color color() {
        return style.color();
    }
}
