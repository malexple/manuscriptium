package ru.mcs.core.shell;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Supplier;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import ru.mcs.core.action.ActionManagerImpl;
import ru.mcs.core.action.ActionMenuBar;
import ru.mcs.core.action.ActionToolbar;
import ru.mcs.core.editor.EditorTabs;
import ru.mcs.core.editor.FileEditorManagerImpl;
import ru.mcs.core.statusbar.StatusBarModel;
import ru.mcs.core.statusbar.StatusBarPanel;
import ru.mcs.core.toolwindow.DockingPanel;
import ru.mcs.core.toolwindow.ToolWindowManagerImpl;
import ru.mcs.spi.action.ActionIds;
import ru.mcs.spi.action.ActionPlaces;
import ru.mcs.spi.action.DataContext;
import ru.mcs.spi.editor.FileEditor;

/** Оболочка: меню и тулбар, панели со stripes, вкладки редактора, статус-бар. */
public final class ShellFrame extends JFrame {

    private final DockingPanel docking;
    private final FileEditorManagerImpl editors;
    private final StatusBarModel statusModel;

    public ShellFrame(String title, ActionManagerImpl actions, ToolWindowManagerImpl toolWindows,
                      FileEditorManagerImpl editors, StatusBarModel statusModel) {
        super(title);
        this.editors = editors;
        this.statusModel = statusModel;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        Supplier<DataContext> ctx = () -> new ShellDataContext(editors);

        setJMenuBar(new ActionMenuBar(actions, ctx));
        add(new ActionToolbar(actions, ActionIds.MAIN_TOOLBAR, ActionPlaces.MAIN_TOOLBAR, ctx), BorderLayout.NORTH);

        docking = new DockingPanel(toolWindows);
        docking.setCenter(new EditorTabs(editors, actions, ctx));
        add(docking, BorderLayout.CENTER);
        add(new StatusBarPanel(statusModel), BorderLayout.SOUTH);

        editors.setCloseConfirmer(this::askAboutUnsaved);
        editors.setErrorReporter((path, ex) -> SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, "Save failed: " + path + "\n" + ex.getMessage(),
                        "Save", JOptionPane.ERROR_MESSAGE)));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (editors.confirmAll()) {
                    System.exit(0);
                }
            }
        });

        setSize(1200, 800);
        setLocationRelativeTo(null);
    }

    private FileEditorManagerImpl.CloseDecision askAboutUnsaved(FileEditor editor) {
        Object[] options = {"Save", "Don't Save", "Cancel"};
        int answer = JOptionPane.showOptionDialog(this, "Save changes to " + editor.getName() + "?",
                "Unsaved changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        return switch (answer) {
            case 0 -> FileEditorManagerImpl.CloseDecision.SAVE;
            case 1 -> FileEditorManagerImpl.CloseDecision.DISCARD;
            default -> FileEditorManagerImpl.CloseDecision.CANCEL;
        };
    }

    public FileEditorManagerImpl editors() {
        return editors;
    }

    public DockingPanel docking() {
        return docking;
    }

    public void setStatus(String text) {
        statusModel.setMessage(text);
    }
}
