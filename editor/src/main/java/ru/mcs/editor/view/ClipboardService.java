package ru.mcs.editor.view;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Optional;

/** Текстовый буфер обмена без зависимости от модели редактора; системный Clipboard создаётся лениво. */
public final class ClipboardService {

    private final Clipboard testClipboard;

    public ClipboardService() {
        testClipboard = null;
    }

    public ClipboardService(Clipboard clipboard) {
        testClipboard = clipboard;
    }

    public void copy(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            clipboard().setContents(new StringSelection(text), null);
        } catch (IllegalStateException | java.awt.HeadlessException ignored) {
            // Буфер может быть временно занят другим приложением; редактирование при этом не должно падать.
        }
    }

    public Optional<String> paste() {
        try {
            Transferable contents = clipboard().getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return Optional.ofNullable((String) contents.getTransferData(DataFlavor.stringFlavor));
            }
        } catch (Exception ignored) {
            // Clipboard — внешний ресурс ОС; отсутствие текста эквивалентно пустой вставке.
        }
        return Optional.empty();
    }

    public Optional<BufferedImage> image() {
        try {
            Transferable contents = clipboard().getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                return Optional.ofNullable((BufferedImage) contents.getTransferData(DataFlavor.imageFlavor));
            }
        } catch (Exception ignored) {
            // Неподдерживаемый или занятый clipboard не должен прерывать текстовую вставку.
        }
        return Optional.empty();
    }

    public List<java.nio.file.Path> files() {
        try {
            Transferable contents = clipboard().getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) contents.getTransferData(DataFlavor.javaFileListFlavor);
                return files.stream().map(File::toPath).toList();
            }
        } catch (Exception ignored) {
            // Неподдерживаемый или занятый clipboard эквивалентен отсутствию файлов.
        }
        return List.of();
    }

    private Clipboard clipboard() {
        return testClipboard != null ? testClipboard : Toolkit.getDefaultToolkit().getSystemClipboard();
    }
}
