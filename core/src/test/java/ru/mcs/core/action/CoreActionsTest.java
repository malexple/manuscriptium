package ru.mcs.core.action;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.mcs.spi.action.ActionIds;

class CoreActionsTest {

    @Test
    void macOsKeepsActionsRegisteredButRemovesMenuDuplicates() {
        ActionManagerImpl actions = new ActionManagerImpl();
        CoreActions.registerStandardGroups(actions);

        CoreActions.registerShellActions(actions, null, true);

        assertThat(actions.getAction("core.exit")).isNotNull();
        assertThat(actions.getAction("core.about")).isNotNull();
        assertThat(actions.childIds(ActionIds.FILE_MENU)).doesNotContain("core.exit");
        assertThat(actions.childIds(ActionIds.HELP_MENU)).doesNotContain("core.about");
    }

    @Test
    void otherPlatformsKeepExitAndAboutInSwingMenus() {
        ActionManagerImpl actions = new ActionManagerImpl();
        CoreActions.registerStandardGroups(actions);

        CoreActions.registerShellActions(actions, null, false);

        assertThat(actions.childIds(ActionIds.FILE_MENU)).contains("core.exit");
        assertThat(actions.childIds(ActionIds.HELP_MENU)).contains("core.about");
    }
}
