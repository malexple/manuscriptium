package ru.mcs.plugin.mdeditor.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.plugin.mdeditor.model.DocumentModelImpl;
import ru.mcs.plugin.mdeditor.model.DocumentProperties;
import ru.mcs.plugin.mdeditor.model.LineEnding;
import ru.mcs.plugin.mdeditor.widget.WidgetRegistry;

class ImageInputServiceTest {

    @TempDir
    Path temp;

    @Test
    void importsSameFilenameWithoutOverwriteAndInsertsRelativeMarkdown() throws Exception {
        Path first = temp.resolve("source-one/photo.png");
        Path second = temp.resolve("source-two/photo.png");
        Files.createDirectories(first.getParent());
        Files.createDirectories(second.getParent());
        Files.writeString(first, "first");
        Files.writeString(second, "second");
        Path noteDirectory = temp.resolve("note");
        Files.createDirectories(noteDirectory);
        EditorCanvas canvas = new EditorCanvas();
        try {
            SwingUtilities.invokeAndWait(() -> {
                canvas.setDocument(new DocumentModelImpl("", new DocumentProperties(StandardCharsets.UTF_8,
                        LineEnding.LF, false, false)));
                canvas.setWidgets(new WidgetRegistry(Runnable::run, null), noteDirectory);
                canvas.importImages(List.of(first), null, 0);
            });
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> canvas.importImages(List.of(second), null, canvas.document().length()));
            SwingUtilities.invokeAndWait(() -> { });

            assertThat(canvas.document().text().toString()).isEqualTo("![](assets/photo.png)\n![](assets/photo-1.png)");
            assertThat(Files.readString(noteDirectory.resolve("assets/photo.png"))).isEqualTo("first");
            assertThat(Files.readString(noteDirectory.resolve("assets/photo-1.png"))).isEqualTo("second");
        } finally {
            SwingUtilities.invokeAndWait(canvas::dispose);
        }
    }

    @Test
    void canvasRegistersNativeDropTarget() throws Exception {
        EditorCanvas canvas = new EditorCanvas();
        try {
            assertThat(canvas.getDropTarget()).isNotNull();
        } finally {
            SwingUtilities.invokeAndWait(canvas::dispose);
        }
    }

    @Test
    void clipboardImageUsesNoteRelativeAssetsPath() throws Exception {
        Path noteDirectory = temp.resolve("note");
        Files.createDirectories(noteDirectory);
        EditorCanvas canvas = new EditorCanvas();
        try {
            SwingUtilities.invokeAndWait(() -> {
                canvas.setDocument(new DocumentModelImpl("", new DocumentProperties(StandardCharsets.UTF_8,
                        LineEnding.LF, false, false)));
                canvas.setWidgets(new WidgetRegistry(Runnable::run, null), noteDirectory);
                canvas.importImages(List.of(), new BufferedImage(8, 6, BufferedImage.TYPE_INT_ARGB), 0);
            });
            SwingUtilities.invokeAndWait(() -> { });

            assertThat(canvas.document().text().toString()).isEqualTo("![](assets/clipboard-image.png)");
            assertThat(noteDirectory.resolve("assets/clipboard-image.png")).isRegularFile();
        } finally {
            SwingUtilities.invokeAndWait(canvas::dispose);
        }
    }
}
