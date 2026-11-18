package ru.mcs.core.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.spi.action.DataContext;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.action.AnActionEvent;

class EditorTabsEmptyStateTest {

    @Test
    void quickStartButtonsInvokeRegisteredProductActions() throws Exception {
        FileEditorManagerImpl editors = new FileEditorManagerImpl();
        ActionManagerImpl actions = new ActionManagerImpl();
        Map<String, AtomicInteger> calls = new LinkedHashMap<>();
        Map<String, String> buttons = new LinkedHashMap<>();
        buttons.put("core.file.new", "editor.empty.newFile");
        buttons.put("core.project.new", "editor.empty.newProject");
        buttons.put("core.project.open", "editor.empty.openProject");
        buttons.put("core.file.open", "editor.empty.openFile");
        for (String id : buttons.keySet()) {
            calls.put(id, new AtomicInteger());
            actions.registerAction(id, new AnAction(id) {
                @Override
                public void actionPerformed(AnActionEvent event) {
                    calls.get(id).incrementAndGet();
                }
            });
        }

        AtomicReference<EditorTabs> created = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> created.set(new EditorTabs(editors, actions, () -> DataContext.EMPTY)));
        for (Map.Entry<String, String> entry : buttons.entrySet()) {
            JButton button = findButton(created.get(), entry.getValue());
            assertThat(button).as(entry.getValue()).isNotNull();
            SwingUtilities.invokeAndWait(button::doClick);
            assertThat(calls.get(entry.getKey())).hasValue(1);
        }
    }

    private static JButton findButton(Container container, String name) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button && name.equals(button.getName())) {
                return button;
            }
            if (child instanceof Container nested) {
                JButton found = findButton(nested, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
