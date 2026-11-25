package ru.mcs.markdown.editor;

import java.awt.BorderLayout;
import java.util.function.UnaryOperator;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import ru.mcs.spi.swing.settings.Configurable;
import ru.mcs.spi.swing.settings.ConfigurableProvider;
import ru.mcs.spi.swing.settings.SettingsGroups;
import ru.mcs.spi.swing.settings.SettingsManager;

/** Страницы Markdown в группах редактора: Code Style → Markdown и Smart Keys → Markdown. */
public final class MarkdownSettingsPages {

    public static final String CODE_STYLE_ID = "markdown.settings.codestyle";
    public static final String SMART_KEYS_ID = "markdown.settings.smartkeys";

    private MarkdownSettingsPages() {
    }

    public static void register(SettingsManager settings, UnaryOperator<String> text) {
        settings.register(page(CODE_STYLE_ID, SettingsGroups.CODE_STYLE, text));
        settings.register(page(SMART_KEYS_ID, SettingsGroups.SMART_KEYS, text));
    }

    private static ConfigurableProvider page(String id, String parent, UnaryOperator<String> text) {
        return new ConfigurableProvider() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getDisplayName() {
                return text.apply("settings.markdown.language");
            }

            @Override
            public String getParentId() {
                return parent;
            }

            @Override
            public Configurable createConfigurable() {
                return new PendingPage(text.apply("settings.pending"));
            }
        };
    }

    /** Заглушка до появления параметров страницы. */
    private static final class PendingPage implements Configurable {

        private final String message;

        private PendingPage(String message) {
            this.message = message;
        }

        @Override
        public JComponent createComponent() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            panel.add(new JLabel(message), BorderLayout.NORTH);
            return panel;
        }

        @Override
        public boolean isModified() {
            return false;
        }

        @Override
        public void apply() {
        }
    }
}
