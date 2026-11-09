package ru.mcs.core.plugin.contrib;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.core.action.CoreActions;
import ru.mcs.core.editor.FileEditorManagerImpl;
import ru.mcs.core.plugin.PluginDescriptor;
import ru.mcs.core.plugin.PluginDescriptorParser;
import ru.mcs.core.shell.PluginContextImpl;
import ru.mcs.core.toolwindow.LayoutStore;
import ru.mcs.core.toolwindow.ToolWindowManagerImpl;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.i18n.I18n;

class ContributionRegistrarI18nTest {

    private static final String YML = """
            id: p
            version: 1.0.0
            sdk-version: "0.8"
            module: p.mod
            i18n: p/messages
            contributions:
              toolWindows:
                - {id: p.key, title: "%tool.title", anchor: left}
                - {id: p.literal, title: "Plain title", anchor: right}
                - {id: p.escaped, title: "%%percent", anchor: bottom}
            """;

    private final I18n fake = new I18n() {
        @Override
        public Locale getLocale() {
            return Locale.ENGLISH;
        }

        @Override
        public String get(String key, Object... args) {
            return "T[" + key + "]";
        }
    };

    @Test
    void percentKeysAreTranslatedLiteralsAndEscapesAreKept() {
        ActionManagerImpl actions = new ActionManagerImpl();
        CoreActions.registerStandardGroups(actions);
        ToolWindowManagerImpl toolWindows = new ToolWindowManagerImpl(actions, new LayoutStore());
        PluginDescriptor d = PluginDescriptorParser.parse(new StringReader(YML), Path.of("."));
        PluginContext ctx = new PluginContextImpl("p", actions.scopedFor("p"), toolWindows.scopedFor("p"),
                new FileEditorManagerImpl().scopedFor("p"), null, null, null, null, null, fake);

        ContributionRegistrar.register(d, ctx, id -> {
            throw new IllegalStateException("must not load");
        });

        assertThat(d.i18n()).isEqualTo("p/messages");
        assertThat(toolWindows.get("p.key").getTitle()).isEqualTo("T[tool.title]");
        assertThat(toolWindows.get("p.literal").getTitle()).isEqualTo("Plain title");
        assertThat(toolWindows.get("p.escaped").getTitle()).isEqualTo("%percent");
    }
}
