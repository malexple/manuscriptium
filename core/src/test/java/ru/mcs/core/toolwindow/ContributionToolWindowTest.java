package ru.mcs.core.toolwindow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.core.action.CoreActions;
import ru.mcs.core.editor.FileEditorManagerImpl;
import ru.mcs.core.plugin.PluginActivator;
import ru.mcs.core.plugin.PluginDescriptor;
import ru.mcs.core.plugin.PluginDescriptorParser;
import ru.mcs.core.plugin.contrib.ContributionRegistrar;
import ru.mcs.core.shell.PluginContextImpl;
import ru.mcs.spi.Plugin;
import ru.mcs.spi.PluginContext;

class ContributionToolWindowTest {

    private static final String YML = """
            id: p
            version: 1.0.0
            sdk-version: "0.7"
            module: p.mod
            activation: onDemand
            contributions:
              toolWindows:
                - {id: p.hidden, title: Hidden, anchor: left}
                - {id: p.shown, title: Shown, anchor: bottom, show-by-default: true}
            """;

    private ToolWindowManagerImpl toolWindows;
    private PluginContext ctx;
    private PluginDescriptor descriptor;
    private final JLabel label = new JLabel("content");
    private final AtomicInteger activations = new AtomicInteger();

    private final Plugin plugin = new Plugin() {
        @Override
        public void start(PluginContext context) {
        }

        @Override
        public JComponent createToolWindowContent(String id) {
            return label;
        }
    };

    @BeforeEach
    void setUp() {
        ActionManagerImpl actions = new ActionManagerImpl();
        CoreActions.registerStandardGroups(actions);
        toolWindows = new ToolWindowManagerImpl(actions, new LayoutStore());
        descriptor = PluginDescriptorParser.parse(new StringReader(YML), Path.of("."));
        ctx = new PluginContextImpl("p", actions.scopedFor("p"), toolWindows.scopedFor("p"),
                new FileEditorManagerImpl().scopedFor("p"), null, null, null, null, null);
    }

    private void register(PluginActivator activator) {
        ContributionRegistrar.register(descriptor, ctx, activator);
    }

    @Test
    void panelsAreRegisteredWithoutLoadingThePlugin() {
        register(id -> {
            activations.incrementAndGet();
            return plugin;
        });
        assertThat(toolWindows.ids()).containsExactly("p.hidden", "p.shown");
        assertThat(toolWindows.get("p.hidden").isVisible()).isFalse();
        assertThat(toolWindows.get("p.shown").isVisible()).isTrue();
        assertThat(activations.get()).isZero();
    }

    @Test
    void firstContentRequestLoadsThePlugin() {
        register(id -> {
            activations.incrementAndGet();
            return plugin;
        });
        assertThat(toolWindows.get("p.hidden").content()).isNotNull();
        assertThat(activations.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void failedActivationDoesNotBreakContentRequest() {
        register(id -> {
            throw new IllegalStateException("cannot load");
        });
        assertThat(toolWindows.get("p.hidden").content()).isNotNull();
    }
}
