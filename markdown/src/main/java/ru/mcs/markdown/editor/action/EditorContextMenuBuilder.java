package ru.mcs.markdown.editor.action;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import ru.mcs.markdown.editor.MarkdownMessages;
import ru.mcs.spi.action.ActionPlaces;
import ru.mcs.spi.swing.action.ActionManager;
import ru.mcs.spi.swing.action.AnActionEvent;

/**
 * Контекстное меню в стиле Typora: буфер обмена, быстрые кнопки формата, затем тип абзаца и вставка.
 * Действия те же, что в главном меню, поэтому состояние и обработчики не дублируются.
 */
public final class EditorContextMenuBuilder {

    /** Порядок соответствует двум рядам кнопок: четыре inline-формата и четыре блока. */
    static final List<String> QUICK_FORMAT = List.of(
            EditorActionIds.BOLD, EditorActionIds.ITALIC, EditorActionIds.INLINE_CODE, EditorActionIds.LINK,
            EditorActionIds.BLOCK_QUOTE, EditorActionIds.ORDERED_LIST, EditorActionIds.BULLET_LIST,
            EditorActionIds.TASK_LIST);

    private static final List<QuickFormatIcon.Kind> QUICK_ICONS = List.of(
            QuickFormatIcon.Kind.BOLD, QuickFormatIcon.Kind.ITALIC, QuickFormatIcon.Kind.ASTERISK,
            QuickFormatIcon.Kind.LINK, QuickFormatIcon.Kind.QUOTE, QuickFormatIcon.Kind.ORDERED,
            QuickFormatIcon.Kind.BULLET, QuickFormatIcon.Kind.TASK);

    private static final List<String> INSERT_BLOCKS = List.of(
            EditorActionIds.LINK, EditorActionIds.HORIZONTAL_RULE, EditorActionIds.TABLE,
            EditorActionIds.CODE_BLOCK, EditorActionIds.MATH_BLOCK);

    private final ActionManager actions;

    public EditorContextMenuBuilder(ActionManager actions) {
        this.actions = actions;
    }

    /** Все команды, которые меню предлагает для контекста, без дублей и в порядке показа. */
    public List<String> actionIds(EditorBlockContext context) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(EditorActionIds.CUT);
        ids.add(EditorActionIds.COPY);
        ids.add(EditorActionIds.PASTE);
        ids.add(EditorActionIds.PASTE_PLAIN);
        ids.addAll(QUICK_FORMAT);
        for (int level = 1; level <= 6; level++) {
            ids.add(EditorActionIds.heading(level));
        }
        ids.add(EditorActionIds.PARAGRAPH);
        ids.add(EditorActionIds.IMAGE);
        ids.addAll(INSERT_BLOCKS);
        return ids.stream().filter(id -> actions.getAction(id) != null).toList();
    }

    public JPopupMenu build(EditorBlockContext context) {
        JPopupMenu popup = new JPopupMenu();
        command(popup, EditorActionIds.CUT, null);
        command(popup, EditorActionIds.COPY, null);
        command(popup, EditorActionIds.PASTE, null);
        popup.add(copyPasteAsMenu());
        popup.addSeparator();
        popup.add(quickFormatPanel(popup));
        popup.addSeparator();
        popup.add(paragraphTypeMenu(context));
        popup.add(insertMenu());
        return popup;
    }

    private JMenu copyPasteAsMenu() {
        JMenu menu = new JMenu(MarkdownMessages.get("contextMenu.copyPasteAs"));
        menu.setName("markdown.editor.menu.copyPasteAs");
        command(menu.getPopupMenu(), EditorActionIds.COPY, MarkdownMessages.get("contextMenu.copyAsMarkdown"));
        command(menu.getPopupMenu(), EditorActionIds.PASTE_PLAIN, null);
        return menu;
    }

    private JPanel quickFormatPanel(JPopupMenu popup) {
        JPanel panel = new JPanel(new GridLayout(2, 4, 4, 4));
        panel.setName("markdown.editor.quickFormat");
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        for (int index = 0; index < QUICK_FORMAT.size(); index++) {
            String id = QUICK_FORMAT.get(index);
            AnActionEvent event = EditorActionDispatcher.update(actions, id, ActionPlaces.POPUP);
            JButton button = new JButton(new QuickFormatIcon(QUICK_ICONS.get(index)));
            button.setName(id);
            button.setFocusable(false);
            button.putClientProperty("JButton.buttonType", "roundRect");
            button.setPreferredSize(new Dimension(40, 22));
            boolean available = event != null && event.getPresentation().isVisible()
                    && event.getPresentation().isEnabled();
            button.setEnabled(available);
            if (event != null) {
                button.setToolTipText(event.getPresentation().getText());
            }
            button.addActionListener(e -> {
                popup.setVisible(false);
                EditorActionDispatcher.perform(actions, id, ActionPlaces.POPUP);
            });
            panel.add(button);
        }
        return panel;
    }

    /** Заголовок пункта показывает текущий тип блока; в подменю текущий отмечен. */
    private JMenu paragraphTypeMenu(EditorBlockContext context) {
        int current = context.headingLevel();
        JMenu menu = new JMenu(text(current > 0 ? EditorActionIds.heading(current) : EditorActionIds.PARAGRAPH));
        menu.setName("markdown.editor.menu.paragraphType");
        for (int level = 1; level <= 6; level++) {
            check(menu.getPopupMenu(), EditorActionIds.heading(level), level == current);
        }
        menu.getPopupMenu().addSeparator();
        check(menu.getPopupMenu(), EditorActionIds.PARAGRAPH, current == 0);
        return menu;
    }

    private JMenu insertMenu() {
        JMenu menu = new JMenu(MarkdownMessages.get("contextMenu.insert"));
        menu.setName("markdown.editor.menu.insert");
        command(menu.getPopupMenu(), EditorActionIds.IMAGE, null);
        menu.getPopupMenu().addSeparator();
        for (String id : INSERT_BLOCKS) {
            command(menu.getPopupMenu(), id, null);
        }
        return menu;
    }

    private void command(JPopupMenu menu, String id, String label) {
        AnActionEvent event = EditorActionDispatcher.update(actions, id, ActionPlaces.POPUP);
        if (event == null || !event.getPresentation().isVisible()) {
            return;
        }
        JMenuItem item = new JMenuItem(label != null ? label : event.getPresentation().getText(),
                event.getPresentation().getIcon());
        item.setName(id);
        item.setEnabled(event.getPresentation().isEnabled());
        item.setToolTipText(event.getPresentation().getDescription());
        item.addActionListener(e -> EditorActionDispatcher.perform(actions, id, ActionPlaces.POPUP));
        menu.add(item);
    }

    private void check(JPopupMenu menu, String id, boolean selected) {
        AnActionEvent event = EditorActionDispatcher.update(actions, id, ActionPlaces.POPUP);
        if (event == null || !event.getPresentation().isVisible()) {
            return;
        }
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(event.getPresentation().getText(), selected);
        item.setName(id);
        item.setEnabled(event.getPresentation().isEnabled());
        item.setToolTipText(event.getPresentation().getDescription());
        item.addActionListener(e -> EditorActionDispatcher.perform(actions, id, ActionPlaces.POPUP));
        menu.add(item);
    }

    private String text(String id) {
        AnActionEvent event = EditorActionDispatcher.update(actions, id, ActionPlaces.POPUP);
        return event == null ? id : event.getPresentation().getText();
    }
}
