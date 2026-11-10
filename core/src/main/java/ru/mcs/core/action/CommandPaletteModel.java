package ru.mcs.core.action;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.Icon;
import ru.mcs.spi.action.ActionGroup;
import ru.mcs.spi.action.ActionPlaces;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.action.DataContext;
import ru.mcs.spi.action.Presentation;
import ru.mcs.spi.action.Separator;

/** Снимок видимых действий и их текстовый фильтр. Вызывается на EDT. */
final class CommandPaletteModel {

    record Entry(String id, AnAction action, String text, String description, Icon icon, boolean enabled) {
    }

    private static final Comparator<Entry> ORDER = Comparator
            .comparing((Entry e) -> e.text().toLowerCase(Locale.ROOT))
            .thenComparing(Entry::id);

    private final ActionManagerImpl actions;
    private final Supplier<DataContext> context;

    CommandPaletteModel(ActionManagerImpl actions, Supplier<DataContext> context) {
        this.actions = Objects.requireNonNull(actions);
        this.context = Objects.requireNonNull(context);
    }

    List<Entry> search(String query) {
        String[] terms = normalize(query).isEmpty() ? new String[0] : normalize(query).split(" ");
        return actions.getActionIds().stream()
                .map(actions::getAction)
                .filter(Objects::nonNull)
                .filter(a -> !(a instanceof ActionGroup) && !(a instanceof Separator))
                .map(this::entry)
                .filter(Objects::nonNull)
                .filter(e -> matches(e, terms))
                .sorted(ORDER)
                .toList();
    }

    void perform(Entry entry) {
        ActionUtil.perform(entry.action(), ActionPlaces.COMMAND_PALETTE, ActionUtil.context(context));
    }

    private Entry entry(AnAction action) {
        AnActionEvent event = ActionUtil.update(
                action,
                ActionPlaces.COMMAND_PALETTE,
                ActionUtil.context(context)
        );
        Presentation p = event.getPresentation();
        if (!p.isVisible()) {
            return null;
        }
        String id = actions.getId(action);
        String text = p.getText() == null || p.getText().isBlank() ? id : p.getText();
        return new Entry(id, action, text, p.getDescription(), p.getIcon(), p.isEnabled());
    }

    private static boolean matches(Entry entry, String[] terms) {
        if (terms.length == 0) {
            return true;
        }
        String haystack = normalize(entry.text() + " " + entry.id() + " "
                + (entry.description() == null ? "" : entry.description()));
        for (String term : terms) {
            if (!haystack.contains(term)) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}