package ru.mcs.core.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.action.ActionPlaces;
import ru.mcs.spi.action.AnAction;
import ru.mcs.spi.action.AnActionEvent;
import ru.mcs.spi.action.DataContext;
import ru.mcs.spi.action.DefaultActionGroup;

class CommandPaletteModelTest {

    private ActionManagerImpl actions;
    private CommandPaletteModel model;

    @BeforeEach
    void setUp() {
        actions = new ActionManagerImpl();
        model = new CommandPaletteModel(actions, () -> DataContext.EMPTY);
    }

    @Test
    void findsByTextIdDescriptionAndAllWords() {
        register("core.settings", "Open Settings", "Application preferences");
        register("core.close", "Close Project", null);
        register("hello.say", "Say hello", "Hello command");

        assertThat(model.search("settings"))
                .extracting(CommandPaletteModel.Entry::id)
                .containsExactly("core.settings");

        assertThat(model.search("CORE.CLOSE"))
                .extracting(CommandPaletteModel.Entry::id)
                .containsExactly("core.close");

        assertThat(model.search("application open"))
                .extracting(CommandPaletteModel.Entry::id)
                .containsExactly("core.settings");

        assertThat(model.search("missing")).isEmpty();
    }

    @Test
    void excludesGroupsAndInvisibleActionsButKeepsDisabledOnes() {
        actions.registerAction(
                "group",
                new DefaultActionGroup("Group", true)
        );
        register("visible", "Visible", null);

        AnAction hidden = register("hidden", "Hidden", null);
        AnAction disabled = register("disabled", "Disabled", null);

        AnAction updating = new AnAction("Dynamic") {
            @Override
            public void actionPerformed(AnActionEvent e) {
            }

            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setVisible(false);
            }
        };

        actions.registerAction("dynamic", updating);
        disabled.getTemplatePresentation().setEnabled(false);
        hidden.getTemplatePresentation().setVisible(false);

        assertThat(model.search(""))
                .extracting(CommandPaletteModel.Entry::id)
                .containsExactly("disabled", "visible");

        assertThat(model.search("disabled").get(0).enabled()).isFalse();
    }

    @Test
    void usesDedicatedActionPlaceAndCurrentDataContext() {
        AtomicReference<AnActionEvent> seen = new AtomicReference<>();

        DataContext expected = new DataContext() {
            @Override
            public <T> T get(ru.mcs.spi.action.DataKey<T> key) {
                return null;
            }
        };

        model = new CommandPaletteModel(actions, () -> expected);

        actions.registerAction("probe", new AnAction("Probe") {
            @Override
            public void actionPerformed(AnActionEvent e) {
            }

            @Override
            public void update(AnActionEvent e) {
                seen.set(e);
            }
        });

        model.search("probe");

        assertThat(seen.get().getPlace())
                .isEqualTo(ActionPlaces.COMMAND_PALETTE);
        assertThat(seen.get().getDataContext()).isSameAs(expected);
    }

    @Test
    void sortsCaseInsensitivelyByTextThenId() {
        register("z", "alpha", null);
        register("b", "Beta", null);
        register("a", "Alpha", null);

        assertThat(model.search(""))
                .extracting(CommandPaletteModel.Entry::id)
                .containsExactly("a", "z", "b");
    }

    private AnAction register(
            String id,
            String text,
            String description
    ) {
        AnAction action = new AnAction(text, description, null) {
            @Override
            public void actionPerformed(AnActionEvent e) {
            }
        };

        actions.registerAction(id, action);
        return action;
    }
}