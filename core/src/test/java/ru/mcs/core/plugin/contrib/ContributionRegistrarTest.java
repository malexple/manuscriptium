package ru.mcs.core.plugin.contrib;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.core.action.CoreActions;
import ru.mcs.core.editor.FileEditorManagerImpl;
import ru.mcs.core.plugin.PluginActivator;
import ru.mcs.core.plugin.PluginDescriptor;
import ru.mcs.core.plugin.PluginDescriptorParser;
import ru.mcs.core.shell.PluginContextImpl;
import ru.mcs.core.toolwindow.LayoutStore;
import ru.mcs.core.toolwindow.ToolWindowManagerImpl;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.action.ActionIds;

class ContributionRegistrarTest {

    private static final String YML = """
            id: p
            version: 1.0.0
            sdk-version: "0.7"
            module: p.mod
            activation: onDemand
            contributions:
              actionGroups:
                - id: p.group
                  text: Group
                  groups: [MainMenu.Tools]
              actions:
                - id: p.a
                  text: A
                  groups: [p.group, MainToolbar]
                - id: p.b
                  text: B
                  groups: [p.group]
              toolWindows:
                - id: p.tool
                  title: Tool
                  anchor: left
              fileEditors:
                - id: p.editor
                  patterns: ["*.hello"]
                  priority: 10
            """;

    private ActionManagerImpl actions;
    private ToolWindowManagerImpl toolWindows;
    private FileEditorManagerImpl editors;
    private PluginDescriptor descriptor;
    private PluginContext ctx;
    private TestPlugin plugin;
    private final AtomicInteger activations = new AtomicInteger();
    private PluginActivator activator;

    @BeforeEach
    void setUp() {
        actions = new ActionManagerImpl();
        CoreActions.registerStandardGroups(actions);
        toolWindows = new ToolWindowManagerImpl(actions, new LayoutStore());
        editors = new FileEditorManagerImpl();
        descriptor = PluginDescriptorParser.parse(new StringReader(YML), Path.of("."));
        ctx = new PluginContextImpl("p", actions.scopedFor("p"), toolWindows.scopedFor("p"),
                editors.scopedFor("p"), null, null, null, null, null);
        plugin = new TestPlugin();
        activator = id -> {
            activations.incrementAndGet();
            return plugin;
        };
        ContributionRegistrar.register(descriptor, ctx, activator);
    }

    @Test
    void actionsAndGroupsAreWiredIntoMenusWithoutLoadingThePlugin() {
        assertThat(actions.childIds(ActionIds.TOOLS_MENU)).contains("p.group");
        assertThat(actions.childIds("p.group")).containsExactly("p.a", "p.b");
        assertThat(actions.childIds(ActionIds.MAIN_TOOLBAR)).containsExactly("p.a");
        assertThat(activations.get()).isZero();
    }

    @Test
    void editorProviderMatchesByFileNameAndActivatesPluginOnOpen() {
        assertThat(editors.providerIds()).containsExactly("p.editor");
        assertThat(editors.openFile(Path.of("note.txt"))).isFalse();
        assertThat(activations.get()).isZero();

        assertThat(editors.openFile(Path.of("note.hello"))).isTrue();
        assertThat(activations.get()).isEqualTo(1);
        assertThat(plugin.editedFiles).hasSize(1);
    }

    @Test
    void globsMatchFileNameOnly() {
        ProxyFileEditorProvider provider = new ProxyFileEditorProvider("p", "e", 0,
                List.of("*.hello", "notes-*.md"), activator);
        assertThat(provider.accept(Path.of("a.hello"))).isTrue();
        assertThat(provider.accept(Path.of("some", "dir", "b.hello"))).isTrue();
        assertThat(provider.accept(Path.of("notes-1.md"))).isTrue();
        assertThat(provider.accept(Path.of("c.txt"))).isFalse();
    }

    @Test
    void cleanupRemovesEveryStub() {
        actions.unregisterAll("p");
        toolWindows.unregisterOwner("p");
        editors.unregisterOwner("p");

        assertThat(actions.childIds(ActionIds.TOOLS_MENU)).isEmpty();
        assertThat(actions.childIds(ActionIds.MAIN_TOOLBAR)).isEmpty();
        assertThat(toolWindows.ids()).isEmpty();
        assertThat(editors.providerIds()).isEmpty();
    }
}
