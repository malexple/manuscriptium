package ru.mcs.editor.draw;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;
import ru.mcs.spi.swing.editor.style.ComputedStyle;

/** Видимый текст, стили и монотонная карта границ в исходный документ. */
public final class InlineContent {

    public record Style(ComputedStyle computed, Font font, String linkTarget, int taskOffset, boolean taskChecked) {
        public Style withFont(Font newFont) {
            return new Style(computed, newFont, linkTarget, -1, false);
        }
    }

    public static final class Atom {
        private final Supplier<BufferedImage> image;
        private final String fallbackText;

        public Atom(Supplier<BufferedImage> image, String fallbackText) {
            this.image = image;
            this.fallbackText = fallbackText;
        }

        public BufferedImage image() {
            return image.get();
        }

        public String fallbackText() {
            return fallbackText;
        }
    }

    public record Segment(String text, int[] sourceOffsets, Style style, Atom atom) {
        Segment(String text, int[] sourceOffsets, Style style) {
            this(text, sourceOffsets, style, null);
        }

        public Segment {
            sourceOffsets = Arrays.copyOf(sourceOffsets, sourceOffsets.length);
        }

        public Segment slice(int start, int end) {
            return new Segment(text.substring(start, end),
                    Arrays.copyOfRange(sourceOffsets, start, end + 1), style, atom);
        }
    }

    private final List<Segment> segments = new ArrayList<>();

    public void appendRaw(String visible, int sourceStart, Style style) {
        if (visible.isEmpty()) {
            return;
        }
        int[] map = new int[visible.length() + 1];
        for (int i = 0; i <= visible.length(); i++) {
            map[i] = sourceStart + i;
        }
        segments.add(new Segment(visible, map, style));
    }

    public void appendMapped(String visible, int sourceStart, int sourceEnd, Style style) {
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

    public void appendAtom(Atom atom, int sourceStart, int sourceEnd, Style style) {
        String visible = atom.fallbackText().isEmpty() ? " " : atom.fallbackText();
        int[] map = new int[visible.length() + 1];
        for (int index = 0; index <= visible.length(); index++) {
            map[index] = sourceStart + (int) Math.round((double) index * (sourceEnd - sourceStart) / visible.length());
        }
        segments.add(new Segment(visible, map, style, atom));
    }

    public void append(InlineContent other, int sourceStart, int sourceEnd) {
        if (other.segments.isEmpty()) {
            return;
        }
        int first = segments.size();
        segments.addAll(other.segments);
        Segment firstSegment = segments.get(first);
        int[] firstMap = firstSegment.sourceOffsets();
        firstMap[0] = sourceStart;
        segments.set(first, new Segment(firstSegment.text(), firstMap, firstSegment.style(), firstSegment.atom()));
        Segment lastSegment = segments.getLast();
        int[] lastMap = lastSegment.sourceOffsets();
        lastMap[lastMap.length - 1] = sourceEnd;
        segments.set(segments.size() - 1, new Segment(lastSegment.text(), lastMap, lastSegment.style(), lastSegment.atom()));
    }

    public List<Segment> segments() {
        return List.copyOf(segments);
    }

    public int sourceStart(int fallback) {
        return segments.isEmpty() ? fallback : segments.getFirst().sourceOffsets()[0];
    }

    public int sourceEnd(int fallback) {
        if (segments.isEmpty()) {
            return fallback;
        }
        int[] map = segments.getLast().sourceOffsets();
        return map[map.length - 1];
    }
}
