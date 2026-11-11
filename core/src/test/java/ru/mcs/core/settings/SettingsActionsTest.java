package ru.mcs.core.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.core.action.CoreActions;
import ru.mcs.spi.action.ActionIds;

class SettingsActionsTest {

    @Test
    void macOsKeepsSettingsActionRegisteredButOutsideFileMenu() {
        ActionManagerImpl actions = new ActionManagerImpl();
        CoreActions.registerStandardGroups(actions);

        SettingsActions.register(actions, null, null, () -> { }, true);

        assertThat(actions.getAction(SettingsActions.ACTION_ID)).isNotNull();
        assertThat(actions.childIds(ActionIds.FILE_MENU)).doesNotContain(SettingsActions.ACTION_ID);
    }

    @Test
    void otherPlatformsKeepSettingsInFileMenu() {
        ActionManagerImpl actions = new ActionManagerImpl();
        CoreActions.registerStandardGroups(actions);

        SettingsActions.register(actions, null, null, () -> { }, false);

        assertThat(actions.childIds(ActionIds.FILE_MENU)).contains(SettingsActions.ACTION_ID);
    }
}
