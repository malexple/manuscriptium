package ru.mcs.plugin.mdeditor.model;

/** Полуоткрытый диапазон UTF-16 смещений: start включительно, end исключительно. */
public record Range(int start, int end) {

    public Range {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid range: " + start + ".." + end);
        }
    }

    public int length() {
        return end - start;
    }
}
