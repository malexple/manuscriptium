package ru.mcs.core.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.swing.settings.Configurable;
import ru.mcs.spi.swing.settings.ConfigurableProvider;
import ru.mcs.spi.swing.settings.SettingsGroups;

class EditorSettingsGroupsTest {

    @Test
    void groupsFormEditorTreeAndAcceptPagesFromPlugins() {
        SettingsManagerImpl manager = new SettingsManagerImpl();
        EditorSettingsGroups.register(manager);
        manager.register(new ColorSchemeSettingsProvider(new ColorSchemeServiceImpl()));
        manager.register(new KeymapSettingsProvider(new KeymapServiceImpl(), "editor",
                SettingsGroups.SMART_KEYS, "settings.editor.smartkeys", SettingsGroups.GENERAL));
        manager.register(page("yaml.codestyle", SettingsGroups.CODE_STYLE));
        manager.register(page("yaml.smartkeys", SettingsGroups.SMART_KEYS));

        DefaultTreeModel model = SettingsTree.create(manager.getProviders());

        assertThat(pathTo(model, SettingsGroups.EDITOR)).containsExactly(SettingsGroups.EDITOR);
        assertThat(pathTo(model, SettingsGroups.CODE_STYLE))
                .containsExactly(SettingsGroups.EDITOR, SettingsGroups.CODE_STYLE);
        assertThat(pathTo(model, SettingsGroups.COLOR_SCHEME))
                .containsExactly(SettingsGroups.EDITOR, SettingsGroups.COLOR_SCHEME);
        assertThat(pathTo(model, "yaml.smartkeys")).containsExactly(SettingsGroups.EDITOR, SettingsGroups.GENERAL,
                SettingsGroups.SMART_KEYS, "yaml.smartkeys");
        assertThat(pathTo(model, "yaml.codestyle")).last().isEqualTo("yaml.codestyle");
    }

    @Test
    void groupPagesAreReadOnlyAndHaveTitles() {
        SettingsManagerImpl manager = new SettingsManagerImpl();
        EditorSettingsGroups.register(manager);
        manager.register(new ColorSchemeSettingsProvider(new ColorSchemeServiceImpl()));
        manager.register(new KeymapSettingsProvider(new KeymapServiceImpl(), "editor",
                SettingsGroups.SMART_KEYS, "settings.editor.smartkeys", SettingsGroups.GENERAL));

        for (ConfigurableProvider provider : manager.getProviders()) {
            assertThat(provider.getDisplayName()).isNotBlank();
            Configurable configurable = provider.createConfigurable();
            assertThat(configurable.createComponent()).isNotNull();
            assertThat(configurable.isModified()).isFalse();
        }
    }

    private static List<String> pathTo(DefaultTreeModel model, String id) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        for (Object element : Collections.list(root.breadthFirstEnumeration())) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) element;
            if (node.getUserObject() instanceof ConfigurableProvider provider && provider.getId().equals(id)) {
                List<String> ids = new ArrayList<>();
                for (Object step : node.getUserObjectPath()) {
                    if (step instanceof ConfigurableProvider parent) {
                        ids.add(parent.getId());
                    }
                }
                return ids;
            }
        }
        throw new AssertionError("Page not in tree: " + id);
    }

    private static ConfigurableProvider page(String id, String parent) {
        return new ConfigurableProvider() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getDisplayName() {
                return id;
            }

            @Override
            public String getParentId() {
                return parent;
            }

            @Override
            public Configurable createConfigurable() {
                return null;
            }
        };
    }
}
