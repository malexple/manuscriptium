package ru.mcs.markdown.editor.view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import ru.mcs.spi.i18n.I18n;

/** Правая панель заметок/ревизий, связанная с исходными %%…%% комментариями. */
public final class MarginNotesPane extends JPanel {

    public interface Handler {
        void select(int offset);

        void add(String text);

        void edit(ObsidianComments.Comment comment, String text);
    }

    private final JPanel items = new JPanel();
    private final Handler handler;
    private final I18n i18n;

    public MarginNotesPane(String emptyText, IntConsumer select) {
        this(emptyText, new Handler() {
            @Override
            public void select(int offset) {
                select.accept(offset);
            }

            @Override
            public void add(String text) {
            }

            @Override
            public void edit(ObsidianComments.Comment comment, String text) {
            }
        }, null);
    }

    public MarginNotesPane(String emptyText, Handler handler, I18n i18n) {
        super(new BorderLayout(0, 6));
        this.handler = handler;
        this.i18n = i18n;
        items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JButton add = new JButton(text("notes.add", "Add Note"));
        add.addActionListener(event -> add());
        add(add, BorderLayout.NORTH);
        add(new JScrollPane(items), BorderLayout.CENTER);
        setPreferredSize(new Dimension(250, 100));
        setMinimumSize(new Dimension(180, 100));
        setNotes(List.of(), emptyText);
    }

    public void setComments(String source, String emptyText) {
        setNotes(ObsidianComments.parse(source), emptyText);
    }

    private void setNotes(List<ObsidianComments.Comment> comments, String emptyText) {
        items.removeAll();
        if (comments.isEmpty()) {
            JLabel empty = new JLabel("<html><body style='width:180px'>" + escape(emptyText) + "</body></html>");
            empty.setAlignmentX(LEFT_ALIGNMENT);
            items.add(empty);
        } else {
            for (ObsidianComments.Comment comment : comments) {
                JButton card = new JButton();
                card.setLayout(new BorderLayout());
                card.setHorizontalAlignment(JButton.LEFT);
                card.setFocusable(false);
                String text = comment.text().isEmpty() ? "…" : comment.text();
                JLabel label = new JLabel("<html><body style='width:150px'>" + escape(text) + "</body></html>");
                label.setFont(label.getFont().deriveFont(Font.PLAIN));
                card.add(label, BorderLayout.CENTER);
                JButton edit = new JButton(text("notes.edit", "Edit"));
                edit.setFocusable(false);
                edit.addActionListener(event -> edit(comment));
                card.add(edit, BorderLayout.EAST);
                card.addActionListener(event -> handler.select(comment.start()));
                card.setAlignmentX(LEFT_ALIGNMENT);
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(52, 24 + 18 * (text.length() / 32 + 1))));
                items.add(card);
                items.add(Box.createVerticalStrut(6));
            }
        }
        items.add(Box.createVerticalGlue());
        revalidate();
        repaint();
    }

    private void add() {
        String value = JOptionPane.showInputDialog(this, text("notes.add.prompt", "Margin note:"),
                text("notes.add", "Add Note"), JOptionPane.PLAIN_MESSAGE);
        if (value != null && !value.isBlank()) {
            handler.add(value);
        }
    }

    private void edit(ObsidianComments.Comment comment) {
        String value = (String) JOptionPane.showInputDialog(this, text("notes.edit.prompt", "Margin note:"),
                text("notes.edit", "Edit"), JOptionPane.PLAIN_MESSAGE, null, null, comment.text());
        if (value != null && !value.isBlank()) {
            handler.edit(comment, value);
        }
    }

    private String text(String key, String fallback) {
        if (i18n == null) {
            return fallback;
        }
        String value = i18n.get(key);
        return key.equals(value) ? fallback : value;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\r", "").replace("\n", "<br>");
    }
}
