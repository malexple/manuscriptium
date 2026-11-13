package ru.mcs.plugin.mdeditor.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import ru.mcs.plugin.mdeditor.model.Delete;
import ru.mcs.plugin.mdeditor.model.DocumentModelImpl;
import ru.mcs.plugin.mdeditor.model.DocumentProperties;
import ru.mcs.plugin.mdeditor.model.Insert;
import ru.mcs.plugin.mdeditor.model.LineEnding;
import ru.mcs.plugin.mdeditor.model.Replace;

class DocumentLineIndexTest {

    @Test
    void tracksPhysicalLinesAcrossBoundaryEdits() {
        DocumentModelImpl document = document("one\ntwo\nthree\n");
        DocumentLineIndex index = new DocumentLineIndex(document);
        document.addListener(change -> index.update(document, change));

        document.apply(new Insert(4, " inserted\nline"));
        assertMatches(index, document);
        document.apply(Delete.of(0, 9));
        assertMatches(index, document);
        document.apply(Replace.of(0, document.length(), "x\ny"));
        assertMatches(index, document);
        document.undo().undo();
        assertMatches(index, document);
    }

    @Test
    void randomizedIncrementalIndexMatchesFreshScan() {
        Random random = new Random(0xE2L);
        DocumentModelImpl document = document("alpha\nbeta\n\ngamma\nlast");
        DocumentLineIndex index = new DocumentLineIndex(document);
        document.addListener(change -> index.update(document, change));

        for (int edit = 0; edit < 600; edit++) {
            int length = document.length();
            int start = length == 0 ? 0 : random.nextInt(length + 1);
            int end = length == 0 ? 0 : start + random.nextInt(length - start + 1);
            String inserted = randomText(random);
            switch (random.nextInt(3)) {
                case 0 -> document.apply(new Insert(start, inserted));
                case 1 -> document.apply(Delete.of(start, end));
                default -> document.apply(Replace.of(start, end, inserted));
            }
            List<Integer> expected = new ArrayList<>();
            expected.add(0);
            for (int i = 0; i < document.length(); i++) {
                if (document.text().charAt(i) == '\n') {
                    expected.add(i + 1);
                }
            }
            assertThat(index.lineCount()).as("line count after edit %s", edit).isEqualTo(expected.size());
            for (int line = 0; line < expected.size(); line++) {
                assertThat(index.lineStart(line)).as("line %s after edit %s", line, edit)
                        .isEqualTo(expected.get(line));
            }
        }
    }

    private static DocumentModelImpl document(String text) {
        return new DocumentModelImpl(text,
                new DocumentProperties(StandardCharsets.UTF_8, LineEnding.LF, false, false));
    }

    private static String randomText(Random random) {
        String[] values = {"", "x", "\n", "ab", "\n\n", "q\nr", "😀"};
        return values[random.nextInt(values.length)];
    }

    private static void assertMatches(DocumentLineIndex index, DocumentModelImpl document) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < document.length(); i++) {
            if (document.text().charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        assertThat(index.lineCount()).isEqualTo(starts.size());
        for (int line = 0; line < starts.size(); line++) {
            assertThat(index.lineStart(line)).isEqualTo(starts.get(line));
            int expectedEnd = line + 1 < starts.size() ? starts.get(line + 1) - 1 : document.length();
            assertThat(index.lineEnd(line, document.length())).isEqualTo(expectedEnd);
        }
    }
}
