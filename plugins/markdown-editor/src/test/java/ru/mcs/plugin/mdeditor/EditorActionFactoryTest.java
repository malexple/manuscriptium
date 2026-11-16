package ru.mcs.plugin.mdeditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import ru.mcs.plugin.mdeditor.action.EditorActionIds;
import ru.mcs.spi.action.ActionPlaces;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.action.AnActionEvent;
import ru.mcs.spi.action.DataContext;
import ru.mcs.spi.i18n.I18n;

class EditorActionFactoryTest {

    @Test
    void createsEveryDeclaredEditorActionAndHidesItWithoutEditor() {
        EditorActionFactory factory = new EditorActionFactory(I18n.passthrough(), Optional::empty);

        for (String id : EditorActionIds.ALL) {
            AnAction action = factory.create(id);
            assertThat(action).as(id).isNotNull();
            AnActionEvent event = new AnActionEvent(action.getTemplatePresentation().copy(),
                    ActionPlaces.COMMAND_PALETTE, DataContext.EMPTY);
            action.update(event);
            assertThat(event.getPresentation().isVisible()).as(id).isFalse();
            assertThat(event.getPresentation().isEnabled()).as(id).isFalse();
        }
        assertThat(factory.create("unknown")).isNull();
    }
}
