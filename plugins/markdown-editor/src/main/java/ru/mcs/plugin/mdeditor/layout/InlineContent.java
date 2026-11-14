package ru.mcs.plugin.mdeditor.layout;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ru.mcs.spi.editor.style.ComputedStyle;

/** Видимый текст, стили и монотонная карта границ в исходный Markdown. */
final class InlineContent {

    record Style(ComputedStyle computed, Font font, String linkTarget, int taskOffset, boolean taskChecked) {
        Style withFont(Font newFont) {
            return new Style(computed, newFont, linkTarget, -1, false);
        }
    }

    record Segment(String text, int[] sourceOffsets, Style style) {
        Segment {
            sourceOffsets = Arrays.copyOf(sourceOffsets, sourceOffsets.length);
        }

        Segment slice(int start, int end) {
            return new Segment(text.substring(start, end),
                    Arrays.copyOfRange(sourceOffsets, start, end + 1), style);
        }
    }

    private final List<Segment> segments = new ArrayList<>();

    void appendRaw(String visible, int sourceStart, Style style) {
        if (visible.isEmpty()) {
            return;
        }
        int[] map = new int[visible.length() + 1];
        for (int i = 0; i <= visible.length(); i++) {
            map[i] = sourceStart + i;
        }
        segments.add(new Segment(visible, map, style));
    }

    void appendMapped(String visible, int sourceStart, int sourceEnd, Style style) {
        if (visible.isEmpty()) {
            return;
        }
        if (visible.length() == sourceEnd - sourceStart) {
            appendRaw(visible, sourceStart, style);
            return;
        }
        int[] map = new int[visible.length() + 1];
        for (int i = 0; i <= visible.length(); i++) {
            map[i] = sourceStart + (int) Math.round((double) i * (sourceEnd - sourceStart) / visible.length());
        }
        segments.add(new Segment(visible, map, style));
    }

    void append(InlineContent other, int sourceStart, int sourceEnd) {
        if (other.segments.isEmpty()) {
            return;
        }
        int first = segments.size();
        segments.addAll(other.segments);
        Segment firstSegment = segments.get(first);
        int[] firstMap = firstSegment.sourceOffsets();
        firstMap[0] = sourceStart;
        segments.set(first, new Segment(firstSegment.text(), firstMap, firstSegment.style()));
        Segment lastSegment = segments.getLast();
        int[] lastMap = lastSegment.sourceOffsets();
        lastMap[lastMap.length - 1] = sourceEnd;
        segments.set(segments.size() - 1, new Segment(lastSegment.text(), lastMap, lastSegment.style()));
    }

    List<Segment> segments() {
        return List.copyOf(segments);
    }

    int sourceStart(int fallback) {
        return segments.isEmpty() ? fallback : segments.getFirst().sourceOffsets()[0];
    }

    int sourceEnd(int fallback) {
        if (segments.isEmpty()) {
            return fallback;
        }
        int[] map = segments.getLast().sourceOffsets();
        return map[map.length - 1];
    }
}
