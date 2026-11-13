package ru.mcs.plugin.mdeditor.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Piece Table с неизменяемым исходным буфером и append-only буфером добавлений.
 * Смещения и длины — UTF-16 code units. Класс не потокобезопасен: доступ координирует DocumentModel.
 */
public final class TextBuffer implements CharSequence {

    private enum Source {
        ORIGINAL,
        ADDED
    }

    private record Piece(Source source, int start, int length) {
    }

    private record Location(int index, int start) {
    }

    private final String original;
    private final StringBuilder added = new StringBuilder();
    private List<Piece> pieces = new ArrayList<>();
    private int length;
    private int cachedIndex = -1;
    private int cachedStart;

    public TextBuffer(String original) {
        this.original = Objects.requireNonNull(original, "original");
        length = original.length();
        if (length > 0) {
            pieces.add(new Piece(Source.ORIGINAL, 0, length));
        }
    }

    @Override
    public int length() {
        return length;
    }

    public void insert(int offset, String text) {
        Objects.requireNonNull(text, "text");
        checkPosition(offset, length);
        if (text.isEmpty()) {
            return;
        }
        int addedStart = added.length();
        added.append(text);
        Piece inserted = new Piece(Source.ADDED, addedStart, text.length());
        if (pieces.isEmpty()) {
            pieces.add(inserted);
        } else if (offset == length) {
            pieces.add(inserted);
            mergeAround(pieces.size() - 1);
        } else {
            Location location = locate(offset);
            Piece piece = pieces.get(location.index());
            int inside = offset - location.start();
            if (inside == 0) {
                pieces.add(location.index(), inserted);
                mergeAround(location.index());
            } else {
                Piece left = new Piece(piece.source(), piece.start(), inside);
                Piece right = new Piece(piece.source(), piece.start() + inside, piece.length() - inside);
                pieces.set(location.index(), left);
                pieces.add(location.index() + 1, inserted);
                pieces.add(location.index() + 2, right);
                mergeAround(location.index() + 1);
            }
        }
        length += text.length();
        invalidateLocation();
    }

    public void delete(Range range) {
        checkRange(range, length);
        if (range.length() == 0) {
            return;
        }
        List<Piece> result = new ArrayList<>(pieces.size() + 1);
        int documentStart = 0;
        for (Piece piece : pieces) {
            int documentEnd = documentStart + piece.length();
            int cutStart = Math.max(range.start(), documentStart);
            int cutEnd = Math.min(range.end(), documentEnd);
            if (cutStart >= cutEnd) {
                result.add(piece);
            } else {
                int leftLength = cutStart - documentStart;
                int rightLength = documentEnd - cutEnd;
                if (leftLength > 0) {
                    result.add(new Piece(piece.source(), piece.start(), leftLength));
                }
                if (rightLength > 0) {
                    int rightStart = piece.start() + leftLength + (cutEnd - cutStart);
                    result.add(new Piece(piece.source(), rightStart, rightLength));
                }
            }
            documentStart = documentEnd;
        }
        pieces = result;
        length -= range.length();
        invalidateLocation();
    }

    public String getText(Range range) {
        checkRange(range, length);
        if (range.length() == 0) {
            return "";
        }
        StringBuilder result = new StringBuilder(range.length());
        Location location = locate(range.start());
        int index = location.index();
        int pieceStart = location.start();
        int offset = range.start();
        while (offset < range.end()) {
            Piece piece = pieces.get(index);
            int inside = offset - pieceStart;
            int count = Math.min(piece.length() - inside, range.end() - offset);
            result.append(buffer(piece.source()), piece.start() + inside, piece.start() + inside + count);
            offset += count;
            index++;
            pieceStart += piece.length();
        }
        return result.toString();
    }

    @Override
    public char charAt(int index) {
        checkPosition(index, length + 1);
        if (index == length) {
            throw new IndexOutOfBoundsException("Index " + index + ", length " + length);
        }
        Location location = locate(index);
        Piece piece = pieces.get(location.index());
        int inside = index - location.start();
        return buffer(piece.source()).charAt(piece.start() + inside);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return getText(new Range(start, end));
    }

    @Override
    public String toString() {
        return getText(new Range(0, length));
    }

    int pieceCount() {
        return pieces.size();
    }

    private CharSequence buffer(Source source) {
        return source == Source.ORIGINAL ? original : added;
    }

    private Location locate(int offset) {
        int index = cachedIndex >= 0 && offset >= cachedStart ? cachedIndex : 0;
        int start = cachedIndex >= 0 && offset >= cachedStart ? cachedStart : 0;
        while (index < pieces.size()) {
            Piece piece = pieces.get(index);
            if (offset < start + piece.length()) {
                cachedIndex = index;
                cachedStart = start;
                return new Location(index, start);
            }
            start += piece.length();
            index++;
        }
        throw new IndexOutOfBoundsException("Offset " + offset + ", length " + length);
    }

    private void mergeAround(int index) {
        if (index > 0 && canMerge(pieces.get(index - 1), pieces.get(index))) {
            Piece joined = join(pieces.get(index - 1), pieces.get(index));
            pieces.set(index - 1, joined);
            pieces.remove(index);
            index--;
        }
        if (index + 1 < pieces.size() && canMerge(pieces.get(index), pieces.get(index + 1))) {
            Piece joined = join(pieces.get(index), pieces.get(index + 1));
            pieces.set(index, joined);
            pieces.remove(index + 1);
        }
        invalidateLocation();
    }

    private static boolean canMerge(Piece left, Piece right) {
        return left.source() == right.source() && left.start() + left.length() == right.start();
    }

    private static Piece join(Piece left, Piece right) {
        return new Piece(left.source(), left.start(), left.length() + right.length());
    }

    private void invalidateLocation() {
        cachedIndex = -1;
    }

    static void checkRange(Range range, int length) {
        Objects.requireNonNull(range, "range");
        if (range.end() > length) {
            throw new IndexOutOfBoundsException("Range " + range + ", document length " + length);
        }
    }

    static void checkPosition(int offset, int length) {
        if (offset < 0 || offset > length) {
            throw new IndexOutOfBoundsException("Offset " + offset + ", document length " + length);
        }
    }
}
