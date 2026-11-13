package ru.mcs.plugin.mdeditor.view;

import java.util.ArrayList;
import java.util.List;
import ru.mcs.plugin.mdeditor.model.DocumentChange;
import ru.mcs.plugin.mdeditor.model.DocumentModel;

/** Инкрементальный индекс физических LF-строк. Хранит только offsets, а не копии текста. */
final class DocumentLineIndex {

    record ChangedLines(int firstLine, int oldLastLine, int newLastLine) {
    }

    private final List<Integer> starts = new ArrayList<>();

    DocumentLineIndex(DocumentModel document) {
        starts.add(0);
        CharSequence text = document.text();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
    }

    int lineCount() {
        return starts.size();
    }

    int lineStart(int line) {
        return starts.get(line);
    }

    int lineEnd(int line, int documentLength) {
        int next = line + 1;
        return next < starts.size() ? starts.get(next) - 1 : documentLength;
    }

    int nextLineStart(int line, int documentLength) {
        int next = line + 1;
        return next < starts.size() ? starts.get(next) : documentLength;
    }

    int lineAt(int offset) {
        int low = 0;
        int high = starts.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int start = starts.get(middle);
            if (start <= offset) {
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return Math.max(0, high);
    }

    ChangedLines update(DocumentModel document, DocumentChange change) {
        int firstLine = lineAt(change.start());
        int oldLastLine = lineAt(change.start() + change.oldLen());
        int oldNextLine = oldLastLine + 1;
        int firstStart = starts.get(firstLine);
        int delta = change.newLen() - change.oldLen();
        int boundary = oldNextLine < starts.size()
                ? starts.get(oldNextLine) + delta
                : document.length() + 1;
        List<Integer> replacement = new ArrayList<>();
        replacement.add(firstStart);
        CharSequence text = document.text();
        boolean hasUnchangedNextLine = oldNextLine < starts.size();
        for (int offset = firstStart; offset < boundary && offset < text.length(); offset++) {
            if (text.charAt(offset) == '\n') {
                int newStart = offset + 1;
                if (!hasUnchangedNextLine || newStart < boundary) {
                    replacement.add(newStart);
                }
            }
        }
        starts.subList(firstLine, oldLastLine + 1).clear();
        starts.addAll(firstLine, replacement);
        int newLastLine = firstLine + replacement.size() - 1;
        for (int line = newLastLine + 1; line < starts.size(); line++) {
            starts.set(line, starts.get(line) + delta);
        }
        return new ChangedLines(firstLine, oldLastLine, newLastLine);
    }
}
