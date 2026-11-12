package ru.mcs.core.editor;

import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import ru.mcs.core.vault.VaultPaths;
import ru.mcs.spi.editor.FileEditor;

/** Простой текстовый редактор (UTF-8, сохраняет CRLF, если он был в файле). Временный до Фазы 5. */
final class TextFileEditor implements FileEditor {

    private final Path file;
    private final boolean crlf;
    private final JTextArea area;
    private final JScrollPane scroll;
    private final ObsidianEditorSupport obsidian;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private boolean modified;

    TextFileEditor(Path file, String text, boolean crlf) {
        this(file, text, crlf, null);
    }

    TextFileEditor(Path file, String text, boolean crlf, ObsidianNavigation navigation) {
        this.file = file;
        this.crlf = crlf;
        this.area = new JTextArea(text);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setTabSize(4);
        area.setCaretPosition(0);
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                setModified(true);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                setModified(true);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                setModified(true);
            }
        });
        this.scroll = new JScrollPane(area);
        this.obsidian = VaultPaths.isMarkdown(file) ? new ObsidianEditorSupport(file, area, navigation) : null;
    }

    ObsidianEditorSupport obsidian() {
        return obsidian;
    }

    String text() {
        return area.getText();
    }

    void setText(String text) {
        area.setText(text);
    }

    /** Выбирает Markdown-заголовок или block-id, адресованный через [[note#fragment]]. */
    void selectFragment(String fragment) {
        String text = area.getText();
        Pattern pattern;
        if (fragment.startsWith("^")) {
            pattern = Pattern.compile("(?m)(?:^|\\s)\\^" + Pattern.quote(fragment.substring(1)) + "(?=\\s*$)");
        } else {
            pattern = Pattern.compile("(?m)^ {0,3}#{1,6}\\s+(.+?)\\s*#*\\s*$");
        }
        Matcher matcher = pattern.matcher(text);
        int lineStart = 0;
        while (lineStart <= text.length()) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            matcher.region(lineStart, lineEnd);
            if (matcher.find()) {
                String candidate = fragment.startsWith("^") ? fragment : matcher.group(1).trim();
                if (candidate.equalsIgnoreCase(fragment)) {
                    area.setCaretPosition(lineStart);
                    area.moveCaretPosition(lineEnd);
                    area.requestFocusInWindow();
                    return;
                }
            }
            if (lineEnd == text.length()) {
                break;
            }
            lineStart = lineEnd + 1;
        }
        area.setCaretPosition(0);
        area.requestFocusInWindow();
    }

    @Override
    public Path getFile() {
        return file;
    }

    @Override
    public String getName() {
        return file.getFileName().toString();
    }

    @Override
    public JComponent getComponent() {
        return scroll;
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public void save() throws IOException {
        String text = area.getText();
        if (crlf) {
            text = text.replace("\n", "\r\n");
        }
        Path parent = file.toAbsolutePath().getParent();
        Path tmp = parent.resolve(file.getFileName() + ".mcs-tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        setModified(false);
    }

    @Override
    public void addModifiedListener(Runnable listener) {
        listeners.add(listener);
    }

    private void setModified(boolean value) {
        if (modified != value) {
            modified = value;
            listeners.forEach(Runnable::run);
        }
    }
}
