package ru.mcs.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.ui.ToolWindowAnchor;

class PluginContributionsParserTest {

    private static final String BASE = "id: p\nversion: 1.0.0\nsdk-version: \"0.7\"\nmodule: p.mod\n";

    private static PluginDescriptor parse(String extra) {
        return PluginDescriptorParser.parse(new StringReader(BASE + extra), Path.of("."));
    }

    @Test
    void withoutContributionsPluginLoadsOnStartup() {
        PluginDescriptor d = parse("");
        assertThat(d.activation()).isEqualTo(Activation.ON_STARTUP);
        assertThat(d.contributions().isEmpty()).isTrue();
    }

    @Test
    void parsesAllContributionKinds() {
        PluginDescriptor d = parse("""
                activation: onDemand
                contributions:
                  actionGroups:
                    - id: p.group
                      text: Group
                      popup: false
                      groups: [MainMenu.Tools]
                  actions:
                    - id: p.a
                      text: A
                      groups: [p.group, MainToolbar]
                  toolWindows:
                    - id: p.tool
                      title: Tool
                      anchor: Bottom
                      show-by-default: true
                  fileEditors:
                    - id: p.editor
                      patterns: ["*.hello", "*.hi"]
                      priority: 10
                  settings:
                    - id: p.settings
                      title: Settings
                      parent-id: core.settings.general
                """);

        assertThat(d.activation()).isEqualTo(Activation.ON_DEMAND);
        Contributions c = d.contributions();
        assertThat(c.actionGroups()).containsExactly(
                new Contributions.ActionGroupDecl("p.group", "Group", false, List.of("MainMenu.Tools")));
        assertThat(c.actions()).containsExactly(
                new Contributions.ActionDecl("p.a", "A", List.of("p.group", "MainToolbar")));
        assertThat(c.toolWindows()).containsExactly(
                new Contributions.ToolWindowDecl("p.tool", "Tool", ToolWindowAnchor.BOTTOM, true));
        assertThat(c.fileEditors()).containsExactly(
                new Contributions.EditorDecl("p.editor", List.of("*.hello", "*.hi"), 10));
        assertThat(c.settings()).containsExactly(
                new Contributions.SettingsDecl("p.settings", "Settings", "core.settings.general"));
    }

    @Test
    void defaultsAreApplied() {
        PluginDescriptor d = parse("""
                contributions:
                  actionGroups:
                    - {id: g, text: G}
                  actions:
                    - {id: a, text: A}
                  toolWindows:
                    - {id: t, title: T, anchor: left}
                  fileEditors:
                    - {id: e, patterns: ["*.x"]}
                  settings:
                    - {id: s, title: S}
                """);
        Contributions c = d.contributions();
        assertThat(c.actionGroups().get(0).popup()).isTrue();
        assertThat(c.actionGroups().get(0).groups()).isEmpty();
        assertThat(c.actions().get(0).groups()).isEmpty();
        assertThat(c.toolWindows().get(0).showByDefault()).isFalse();
        assertThat(c.fileEditors().get(0).priority()).isZero();
        assertThat(c.settings().get(0).parentId()).isNull();
    }

    @Test
    void unknownActivationIsRejected() {
        assertThatThrownBy(() -> parse("activation: sometimes\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("activation");
    }

    @Test
    void actionWithoutTextIsRejected() {
        assertThatThrownBy(() -> parse("contributions:\n  actions:\n    - id: a\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("text");
    }

    @Test
    void unknownAnchorIsRejected() {
        assertThatThrownBy(() -> parse("contributions:\n  toolWindows:\n    - {id: t, title: T, anchor: top}\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("anchor");
    }

    @Test
    void duplicateIdsWithinKindAreRejected() {
        assertThatThrownBy(() -> parse("contributions:\n  actions:\n    - {id: a, text: A}\n    - {id: a, text: B}\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
    }

    @Test
    void editorWithoutPatternsIsRejected() {
        assertThatThrownBy(() -> parse("contributions:\n  fileEditors:\n    - {id: e, patterns: []}\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("patterns");
    }

    @Test
    void malformedSectionsAreRejected() {
        assertThatThrownBy(() -> parse("contributions: nope\n")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parse("contributions:\n  actions: nope\n")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parse("contributions:\n  actions:\n    - nope\n")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shortConstructorStillWorks() {
        PluginDescriptor d = new PluginDescriptor("p", "P", "1", "0.7", "m", List.of(), List.of(), Path.of("."));
        assertThat(d.activation()).isEqualTo(Activation.ON_STARTUP);
        assertThat(d.contributions()).isEqualTo(Contributions.NONE);
    }
}
