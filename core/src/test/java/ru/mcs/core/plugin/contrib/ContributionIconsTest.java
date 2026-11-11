package ru.mcs.core.plugin.contrib;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.core.action.CoreActions;
import ru.mcs.core.editor.FileEditorManagerImpl;
import ru.mcs.core.plugin.PluginDescriptor;
import ru.mcs.core.plugin.PluginDescriptorParser;
import ru.mcs.core.settings.SettingsManagerImpl;
import ru.mcs.core.shell.PluginContextImpl;
import ru.mcs.core.toolwindow.LayoutStore;
import ru.mcs.core.toolwindow.ToolWindowManagerImpl;
import ru.mcs.spi.PluginContext;

class ContributionIconsTest {

    @TempDir
    Path directory;

    @Test
    void wiresActionAndToolWindowIconsWithoutActivatingPlugin() throws Exception {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(directory.resolve("plugin.jar")))) {
            output.putNextEntry(new JarEntry("icons/icon.svg"));
            output.write("""
                    <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                      <path d="M4 12 H20" stroke="currentColor" stroke-width="2"/>
                    </svg>
                    """.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        PluginDescriptor descriptor = PluginDescriptorParser.parse(new StringReader("""
                id: p
                version: 1
                sdk-version: "0.9"
                module: p.mod
                activation: onDemand
                contributions:
                  actions:
                    - id: p.a
                      text: A
                      icon: /icons/icon.svg
                      groups: [MainToolbar]
                  toolWindows:
                    - id: p.tool
                      title: Tool
                      icon: /icons/icon.svg
                      anchor: left
                """), directory);
        ActionManagerImpl actions = new ActionManagerImpl();
        CoreActions.registerStandardGroups(actions);
        ToolWindowManagerImpl toolWindows = new ToolWindowManagerImpl(actions, new LayoutStore());
        PluginContext ctx = new PluginContextImpl("p", actions.scopedFor("p"), toolWindows.scopedFor("p"),
                new FileEditorManagerImpl().scopedFor("p"), null, null, null, null, null,
                new SettingsManagerImpl().scopedFor("p"));
        AtomicInteger activations = new AtomicInteger();

        ContributionRegistrar.register(descriptor, ctx, id -> {
            activations.incrementAndGet();
            throw new AssertionError("Must not activate");
        });

        assertThat(actions.getAction("p.a").getTemplatePresentation().getIcon()).isNotNull();
        assertThat(toolWindows.get("p.tool").getIcon()).isNotNull();
        assertThat(activations).hasValue(0);
    }
}
