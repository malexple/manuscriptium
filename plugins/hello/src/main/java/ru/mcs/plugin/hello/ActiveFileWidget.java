package ru.mcs.plugin.hello;

import java.nio.file.Path;
import javax.swing.JComponent;
import javax.swing.JLabel;
import ru.mcs.spi.editor.FileEditorListener;
import ru.mcs.spi.ui.StatusBarWidget;

/** Показывает имя активного файла. Обновляется по сообщениям шины (подписка на EDT). */
final class ActiveFileWidget implements StatusBarWidget, FileEditorListener {

    private JLabel label;

    @Override
    public String getId() {
        return "hello.activeFile";
    }

    @Override
    public Position getPosition() {
        return Position.RIGHT;
    }

    @Override
    public JComponent createComponent() {
        label = new JLabel("No file");
        return label;
    }

    @Override
    public void activeFileChanged(Path file) {
        if (label != null) {
            label.setText(file == null ? "No file" : file.getFileName().toString());
        }
    }
}
