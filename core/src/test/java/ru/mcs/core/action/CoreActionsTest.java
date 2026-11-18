package ru.mcs.core.action;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.mcs.spi.action.ActionIds;

class CoreActionsTest {

    @Test
    void macOsKeepsActionsRegisteredButRemovesMenuDuplicates() {
        ActionManagerImpl actions = new ActionManagerImpl();
        DesktopMetadata metadata = new DesktopMetadata(actions, true);
        metadata.bind();

        CoreActions.registerShellActions(actions, null, true);
        metadata.bind();

        assertThat(actions.getAction("core.exit")).isNotNull();
        assertThat(actions.getAction("core.about")).isNotNull();
        assertThat(actions.childIds(ActionIds.FILE_MENU)).doesNotContain("core.exit");
        assertThat(actions.childIds(ActionIds.HELP_MENU)).doesNotContain("core.about");
    }

    @Test
    void otherPlatformsKeepExitAndAboutInSwingMenus() {
        ActionManagerImpl actions = new ActionManagerImpl();
        DesktopMetadata metadata = new DesktopMetadata(actions, false);
        metadata.bind();

        CoreActions.registerShellActions(actions, null, false);
        metadata.bind();

        assertThat(actions.childIds(ActionIds.FILE_MENU)).contains("core.exit");
        assertThat(actions.childIds(ActionIds.HELP_MENU)).contains("core.about");
    }
}
