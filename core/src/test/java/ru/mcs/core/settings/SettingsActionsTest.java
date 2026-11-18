package ru.mcs.core.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.core.action.CoreMetadataProvider;
import ru.mcs.core.action.DesktopMetadata;
import ru.mcs.metadata.MetadataCatalog;
import ru.mcs.metadata.MetadataDocument;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.swing.action.AnAction;

class SettingsActionsTest {

    @Test
    void macOsKeepsSettingsActionRegisteredButOutsideFileMenu() {
        ActionManagerImpl actions = new ActionManagerImpl();
        DesktopMetadata metadata = new DesktopMetadata(actions, true);
        metadata.bind();

        SettingsActions.register(actions, null, null, () -> { }, true);
        metadata.bind();

        assertThat(actions.getAction(SettingsActions.ACTION_ID)).isNotNull();
        assertThat(actions.childIds(ActionIds.FILE_MENU)).doesNotContain(SettingsActions.ACTION_ID);
    }

    @Test
    void otherPlatformsKeepSettingsInFileMenu() {
        ActionManagerImpl actions = new ActionManagerImpl();
        DesktopMetadata metadata = new DesktopMetadata(actions, false);
        metadata.bind();

        SettingsActions.register(actions, null, null, () -> { }, false);
        MetadataDocument document = MetadataCatalog.from(new CoreMetadataProvider()).get("core").document();
        for (MetadataDocument.Action action : document.actions()) {
            if (actions.getAction(action.id()) == null
                    && !action.id().equals(SettingsActions.ACTION_ID) && !action.id().equals("core.exit")) {
                actions.registerAction(action.id(), stub(action.id()));
            }
        }
        metadata.bind();
        assertThat(actions.childIds(ActionIds.FILE_MENU))
                .as("BEFORE entry ждёт регистрации anchor, а не деградирует в LAST")
                .doesNotContain(SettingsActions.ACTION_ID);

        actions.registerAction("core.exit", stub("core.exit"));
        metadata.bind();

        assertThat(actions.childIds(ActionIds.FILE_MENU)).contains(SettingsActions.ACTION_ID);
    }

    private static AnAction stub(String id) {
        return new AnAction(id) {
            @Override
            public void actionPerformed(ru.mcs.spi.swing.action.AnActionEvent event) {
            }
        };
    }
}
