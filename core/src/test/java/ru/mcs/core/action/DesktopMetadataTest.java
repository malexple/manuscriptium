package ru.mcs.core.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import ru.mcs.metadata.MetadataCatalog;
import ru.mcs.metadata.MetadataDocument;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.action.AnActionEvent;
import ru.mcs.spi.swing.action.Separator;

class DesktopMetadataTest {

    @Test
    void bindsWholeFileMenuToolbarAndKeymapWithoutDuplicates() {
        ActionManagerImpl actions = new ActionManagerImpl();
        DesktopMetadata metadata = new DesktopMetadata(actions,
                MetadataCatalog.from(new CoreMetadataProvider()), false);
        metadata.bind();
        registerCoreActions(actions, metadata);

        assertThat(actions.childIds(ActionIds.FILE_MENU)).containsExactly(
                "core.project.open", "core.project.new", "core.project.newNote", "core.file.new",
                "core.file.open", "core.file.save", "core.file.saveAll", "core.file.tabs.separator",
                "core.tab.close", "core.tab.pin", "core.file.settings.separator", "core.settings.open", "core.exit");
        assertThat(actions.childIds(ActionIds.MAIN_TOOLBAR)).containsExactly(
                "core.project.open", "core.project.new", "core.project.newNote", "core.search.open",
                "core.file.new", "core.file.open", "core.file.save", "core.file.saveAll");
        assertThat(actions.childIds(ActionIds.MAIN_MENU)).containsExactly(
                ActionIds.FILE_MENU, ActionIds.VIEW_MENU, ActionIds.TOOLS_MENU, ActionIds.HELP_MENU);
        assertThat(metadata.keyStroke(CommandPalette.ACTION_ID)).isEqualTo(KeyStroke.getKeyStroke(
                KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertThat(metadata.settingsPage("core.settings.general").fields()).hasSize(1);
    }

    @Test
    void macOsOmitsNativeMenuDuplicatesButKeepsRegisteredCommands() {
        ActionManagerImpl actions = new ActionManagerImpl();
        DesktopMetadata metadata = new DesktopMetadata(actions,
                MetadataCatalog.from(new CoreMetadataProvider()), true);
        metadata.bind();
        registerCoreActions(actions, metadata);

        assertThat(actions.getAction("core.settings.open")).isNotNull();
        assertThat(actions.getAction("core.exit")).isNotNull();
        assertThat(actions.getAction("core.about")).isNotNull();
        assertThat(actions.childIds(ActionIds.FILE_MENU)).doesNotContain(
                "core.settings.open", "core.exit", "core.file.settings.separator");
        assertThat(actions.childIds(ActionIds.HELP_MENU)).doesNotContain("core.about");
    }

    private static void registerCoreActions(ActionManagerImpl actions, DesktopMetadata metadata) {
        MetadataDocument document = MetadataCatalog.from(new CoreMetadataProvider()).get("core").document();
        for (MetadataDocument.Action action : document.actions()) {
            if (actions.getAction(action.id()) == null) {
                actions.registerAction(action.id(), action.id().contains("separator")
                        ? new Separator() : new StubAction());
            }
            // Воспроизводим production: реализации появляются по одной, binder вызывается после каждой.
            metadata.bind();
        }
        metadata.bind();
    }

    private static final class StubAction extends AnAction {
        @Override
        public void actionPerformed(AnActionEvent e) {
        }
    }
}
