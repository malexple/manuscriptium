package ru.mcs.plugin.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Container;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcs.spi.PluginContext;
import ru.mcs.spi.action.ActionManager;
import ru.mcs.spi.editor.FileEditorManager;
import ru.mcs.spi.editor.FileEditorProvider;
import ru.mcs.spi.event.MessageBus;
import ru.mcs.spi.i18n.I18n;
import ru.mcs.spi.service.ServiceRegistry;
import ru.mcs.spi.service.StateManager;
import ru.mcs.spi.settings.SettingsManager;
import ru.mcs.spi.ui.NotificationManager;
import ru.mcs.spi.ui.StatusBar;
import ru.mcs.spi.ui.ToolWindowManager;

class PropertiesPanelTest {

    @TempDir
    Path temp;

    @Test
    void applySavesEditorsAtomicallyReopensNoteAndPreservesUnknownYamlAndBody() throws Exception {
        Path note = Files.writeString(temp.resolve("Note.md"), "---\r\n"
                + "title: Old\r\n"
                + "author: Keep me\r\n"
                + "tags: [old]\r\n"
                + "---\r\n"
                + "# Body\r\nDo not change\r\n");
        FakeEditors editors = new FakeEditors(note);
        TestContext context = new TestContext(editors);
        AtomicReference<PropertiesPanel> created = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> created.set(new PropertiesPanel(context)));
        PropertiesPanel panel = created.get();
        awaitEnabled(panel);

        JTextArea other = component(panel, "properties.other", JTextArea.class);
        assertThat(other.getText()).contains("author: Keep me").doesNotContain("title:", "tags:");
        SwingUtilities.invokeAndWait(() -> {
            component(panel, "properties.title", JTextField.class).setText("New title");
            component(panel, "properties.tags", JTextField.class).setText("java, notes, java");
            component(panel, "properties.draft", JCheckBox.class).setSelected(true);
            component(panel, "properties.apply", JButton.class).doClick();
        });
        awaitApply(editors);

        assertThat(editors.saveCount).isEqualTo(1);
        assertThat(editors.closeCount).isEqualTo(1);
        assertThat(editors.openCount).isEqualTo(1);
        assertThat(Files.readString(note)).isEqualTo("---\r\n"
                + "title: \"New title\"\r\n"
                + "author: Keep me\r\n"
                + "tags: [\"java\", \"notes\"]\r\n"
                + "draft: true\r\n"
                + "---\r\n"
                + "# Body\r\nDo not change\r\n");
        assertThat(context.notifications.errors).isEmpty();
        awaitEnabled(panel);
    }

    @Test
    void noMarkdownSelectionDisablesEditing() throws Exception {
        FakeEditors editors = new FakeEditors(null);
        AtomicReference<PropertiesPanel> created = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> created.set(new PropertiesPanel(new TestContext(editors))));

        assertThat(component(created.get(), "properties.title", JTextField.class).isEnabled()).isFalse();
        assertThat(component(created.get(), "properties.apply", JButton.class).isEnabled()).isFalse();
        assertThat(component(created.get(), "properties.refresh", JButton.class).isEnabled()).isFalse();
    }

    private static void awaitEnabled(PropertiesPanel panel) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> { });
            if (component(panel, "properties.title", JTextField.class).isEnabled()) {
                return;
            }
            Thread.sleep(20);
        }
        assertThat(component(panel, "properties.title", JTextField.class).isEnabled()).isTrue();
    }

    private static void awaitApply(FakeEditors editors) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> { });
            if (editors.openCount == 1) {
                return;
            }
            Thread.sleep(20);
        }
        assertThat(editors.openCount).isEqualTo(1);
    }

    private static <T> T component(Container root, String name, Class<T> type) {
        T result = findComponent(root, name, type);
        if (result == null) {
            throw new AssertionError("Component not found: " + name);
        }
        return result;
    }

    private static <T> T findComponent(Container root, String name, Class<T> type) {
        for (java.awt.Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                T found = findComponent(container, name, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static final class FakeEditors implements FileEditorManager {
        private Path selected;
        private volatile int saveCount;
        private volatile int closeCount;
        private volatile int openCount;

        private FakeEditors(Path selected) {
            this.selected = selected;
        }

        @Override public void registerProvider(FileEditorProvider provider) { }
        @Override public void unregisterProvider(FileEditorProvider provider) { }
        @Override public boolean openFile(Path file) { selected = file; openCount++; return true; }
        @Override public boolean closeFile(Path file) { if (file.equals(selected)) selected = null; closeCount++; return true; }
        @Override public List<Path> getOpenFiles() { return selected == null ? List.of() : List.of(selected); }
        @Override public Optional<Path> getSelectedFile() { return Optional.ofNullable(selected); }
        @Override public void selectFile(Path file) { selected = file; }
        @Override public boolean isPinned(Path file) { return false; }
        @Override public void setPinned(Path file, boolean pinned) { }
        @Override public void saveAll() { saveCount++; }
    }

    private static final class TestContext implements PluginContext {
        private final FileEditorManager editors;
        private final TestNotifications notifications = new TestNotifications();
        private final TestStatusBar status = new TestStatusBar();

        private TestContext(FileEditorManager editors) {
            this.editors = editors;
        }

        @Override public String pluginId() { return "test"; }
        @Override public void log(String message) { }
        @Override public ActionManager actions() { return null; }
        @Override public ToolWindowManager toolWindows() { return null; }
        @Override public FileEditorManager editors() { return editors; }
        @Override public StatusBar statusBar() { return status; }
        @Override public NotificationManager notifications() { return notifications; }
        @Override public MessageBus messageBus() { return null; }
        @Override public ServiceRegistry services() { return null; }
        @Override public StateManager state() { return null; }
        @Override public SettingsManager settings() { return null; }
        @Override public I18n i18n() {
            return new I18n() {
                @Override public Locale getLocale() { return Locale.ENGLISH; }
                @Override public String get(String key, Object... args) { return key; }
            };
        }
    }

    private static final class TestNotifications implements NotificationManager {
        private final List<String> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
        @Override public void notify(String title, String content, ru.mcs.spi.ui.NotificationType type) {
            if (type == ru.mcs.spi.ui.NotificationType.ERROR) errors.add(content);
        }
    }

    private static final class TestStatusBar implements StatusBar {
        @Override public void addWidget(ru.mcs.spi.ui.StatusBarWidget widget) { }
        @Override public void removeWidget(String id) { }
        @Override public void setMessage(String message) { }
        @Override public ru.mcs.spi.ui.StatusBarTask startTask(String title) {
            return new ru.mcs.spi.ui.StatusBarTask() {
                @Override public void setTitle(String title) { }
                @Override public void finish() { }
            };
        }
    }
}
