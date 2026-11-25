package ru.mcs.core.settings;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import ru.mcs.core.i18n.Messages;
import ru.mcs.spi.swing.settings.Configurable;
import ru.mcs.spi.swing.settings.ConfigurableProvider;

/** Страница-группа без параметров: показывает вложенные страницы плагинов в дереве настроек. */
final class GroupSettingsProvider implements ConfigurableProvider {

    private final String id;
    private final String titleKey;
    private final String parentId;

    GroupSettingsProvider(String id, String titleKey, String parentId) {
        this.id = id;
        this.titleKey = titleKey;
        this.parentId = parentId;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return Messages.get(titleKey);
    }

    @Override
    public String getParentId() {
        return parentId;
    }

    @Override
    public Configurable createConfigurable() {
        return new Configurable() {
            @Override
            public JComponent createComponent() {
                JPanel panel = new JPanel(new BorderLayout());
                panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
                panel.add(new JLabel(Messages.get("settings.group.hint")), BorderLayout.NORTH);
                return panel;
            }

            @Override
            public boolean isModified() {
                return false;
            }

            @Override
            public void apply() {
            }
        };
    }
}
