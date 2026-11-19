package ru.mcs.core.action;

import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import ru.mcs.spi.action.ActionPlaces;
import ru.mcs.spi.action.DataContext;
import ru.mcs.spi.swing.action.AnAction;

/** Подключает выбранные declarative keymap-команды ко всему окну, сохраняя общий action update-контракт. */
public final class MetadataKeyBindings {

    private MetadataKeyBindings() {
    }

    public static void install(JRootPane root, ActionManagerImpl actions, Supplier<DataContext> context,
            DesktopMetadata metadata, String... commandIds) {
        for (String commandId : commandIds) {
            root.registerKeyboardAction(event -> {
                AnAction action = actions.getAction(commandId);
                if (action != null) {
                    ActionUtil.perform(action, ActionPlaces.MAIN_MENU, ActionUtil.context(context));
                }
            }, metadata.keyStroke(commandId), JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
    }
}
