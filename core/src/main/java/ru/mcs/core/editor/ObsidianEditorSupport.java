package ru.mcs.core.editor;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import ru.mcs.core.i18n.Messages;

/** Подсветка и Ctrl/Cmd+Click для Obsidian-разметки поверх обычного редактируемого JTextArea. */
final class ObsidianEditorSupport {

    private final Path file;
    private final JTextArea area;
    private final Cursor textCursor;
    private final ObsidianNavigation navigation;
    private volatile ObsidianSyntax.Result result = ObsidianSyntax.Result.empty();
    private boolean refreshPending;

    ObsidianEditorSupport(Path file, JTextArea area, ObsidianNavigation navigation) {
        this.file = file;
        this.area = area;
        this.textCursor = area.getCursor();
        this.navigation = navigation;
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleRefresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleRefresh();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleRefresh();
            }
        });
        area.addPropertyChangeListener("UI", e -> scheduleRefresh());
        area.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e);
            }
        });
        area.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1 && isShortcut(e)) {
                    openAt(area.viewToModel2D(e.getPoint()), area);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                area.setCursor(textCursor);
                area.setToolTipText(null);
            }
        });
        refreshNow();
    }

    ObsidianSyntax.Result result() {
        return result;
    }

    void refreshNow() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refreshNow);
            return;
        }
        refreshPending = false;
        result = ObsidianSyntax.parse(area.getText());
        Highlighter highlighter = area.getHighlighter();
        highlighter.removeAllHighlights();
        try {
            for (ObsidianSyntax.Callout callout : result.callouts()) {
                highlighter.addHighlight(callout.start(), callout.end(),
                        new DefaultHighlighter.DefaultHighlightPainter(calloutColor(callout.type())));
            }
            for (ObsidianSyntax.Link link : result.links()) {
                Color accent = color(link.embed() ? "Component.accentColor" : "Component.linkColor",
                        new Color(70, 125, 210));
                highlighter.addHighlight(link.start(), link.end(),
                        new DefaultHighlighter.DefaultHighlightPainter(alpha(accent, link.embed() ? 72 : 48)));
            }
        } catch (BadLocationException ignored) {
            // Документ мог измениться между разбором и отрисовкой; следующий DocumentEvent перерисует диапазоны.
        }
    }

    boolean openAt(int offset, JComponent anchor) {
        return result.linkAt(offset).map(link -> {
            if (navigation != null) {
                navigation.open(file, link, anchor);
                return true;
            }
            return false;
        }).orElse(false);
    }

    private void scheduleRefresh() {
        if (refreshPending) {
            return;
        }
        refreshPending = true;
        SwingUtilities.invokeLater(this::refreshNow);
    }

    private void updateHover(MouseEvent e) {
        int offset = area.viewToModel2D(e.getPoint());
        result.linkAt(offset).ifPresentOrElse(link -> {
            area.setToolTipText(Messages.get(link.embed() ? "editor.obsidian.embed.tooltip"
                    : "editor.obsidian.link.tooltip", link.navigationTarget()));
            area.setCursor(isShortcut(e) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        }, () -> {
            area.setToolTipText(null);
            area.setCursor(textCursor);
        });
    }

    private static boolean isShortcut(InputEvent e) {
        return e.isControlDown() || e.isMetaDown();
    }

    private static Color calloutColor(String type) {
        String key = type.toLowerCase(Locale.ROOT);
        Color base = switch (key) {
            case "tip", "hint", "success", "check", "done" -> new Color(40, 160, 95);
            case "question", "help", "faq", "warning", "caution", "attention" -> new Color(205, 140, 25);
            case "failure", "fail", "missing", "danger", "error", "bug" -> new Color(215, 75, 75);
            case "example" -> new Color(145, 95, 195);
            case "quote", "cite" -> new Color(125, 130, 140);
            default -> color("Component.accentColor", new Color(70, 125, 210));
        };
        return alpha(base, 34);
    }

    private static Color color(String key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value != null ? value : fallback;
    }

    private static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
