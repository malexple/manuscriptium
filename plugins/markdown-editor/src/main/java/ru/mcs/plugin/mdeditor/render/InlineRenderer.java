package ru.mcs.plugin.mdeditor.render;

import ru.mcs.plugin.mdeditor.layout.InlineRun;
import ru.mcs.plugin.mdeditor.layout.LineBox;
import ru.mcs.spi.editor.style.TextDecoration;

/** Рисование styled inline-runs и декораций без изменения исходных offsets. */
public final class InlineRenderer {

    public void paint(DrawSink sink, LineBox line) {
        for (InlineRun run : line.runs()) {
            if (run.text().isEmpty()) {
                continue;
            }
            if (run.style().backgroundColor().getAlpha() > 0) {
                int radius = Math.max(0, (int) Math.round(run.style().borderRadius().value()));
                sink.fillRoundRect(run.x(), line.y(), Math.max(1, Math.round(run.advance())), line.height(),
                        radius, run.style().backgroundColor());
            }
            if (run.taskOffset() >= 0) {
                paintTaskCheckbox(sink, run, line);
            }
            if (run.image() != null) {
                sink.drawImage(run.image(), run.x(), run.baseline() - run.image().getHeight(),
                        run.image().getWidth(), run.image().getHeight());
            } else {
                sink.drawText(run.textLayout(), run.text(), run.x(), run.baseline(), run.font(), run.color());
            }
            int lineY = run.baseline() + 2;
            if (run.style().textDecorations().contains(TextDecoration.UNDERLINE)) {
                sink.drawLine(run.x(), lineY, run.x() + Math.round(run.advance()), lineY, 1, run.color());
            }
            if (run.style().textDecorations().contains(TextDecoration.LINE_THROUGH)) {
                int strikeY = run.baseline() - Math.max(2, Math.round(run.font().getSize2D() * 0.30f));
                sink.drawLine(run.x(), strikeY, run.x() + Math.round(run.advance()), strikeY, 1, run.color());
            }
        }
    }

    private static void paintTaskCheckbox(DrawSink sink, InlineRun run, LineBox line) {
        int size = Math.max(9, Math.min(12, line.height() - 8));
        int x = run.x();
        int y = line.y() + Math.max(0, (line.height() - size) / 2);
        sink.drawLine(x, y, x + size, y, 1, run.color());
        sink.drawLine(x, y + size, x + size, y + size, 1, run.color());
        sink.drawLine(x, y, x, y + size, 1, run.color());
        sink.drawLine(x + size, y, x + size, y + size, 1, run.color());
        if (run.taskChecked()) {
            sink.drawLine(x + 2, y + size / 2, x + size / 2, y + size - 2, 2, run.color());
            sink.drawLine(x + size / 2, y + size - 2, x + size - 1, y + 2, 2, run.color());
        }
    }
}
