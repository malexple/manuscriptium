package ru.mcs.plugin.notes;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import ru.mcs.spi.editor.FileEditorManager;
import ru.mcs.spi.i18n.I18n;

/** Панель открытых Markdown-вкладок; файловый ввод-вывод не выполняется на EDT. */
final class OpenNotesPanel extends JPanel {

    private final FileEditorManager editors;
    private final I18n i18n;
    private final Consumer<Path> copyAction;
    private final DefaultListModel<Path> model = new DefaultListModel<>();
    private final JList<Path> list = new JList<>(model);
    private final JLabel selectionLabel = new JLabel();
    private final JLabel statisticsLabel = new JLabel();
    private final JButton copyButton;
    private boolean updating;
    private long generation;

    OpenNotesPanel(FileEditorManager editors, I18n i18n, Consumer<Path> copyAction) {
        super(new BorderLayout(6, 6));
        this.editors = editors;
        this.i18n = i18n;
        this.copyAction = copyAction;
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        list.setName("notes.list");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new NoteRenderer());
        list.addListSelectionListener(event -> {
            if (!updating && !event.getValueIsAdjusting()) {
                selectionChanged();
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
                    openSelected();
                }
            }
        });
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openNote");
        list.getActionMap().put("openNote", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                openSelected();
            }
        });

        copyButton = new JButton(i18n.get("notes.copy"));
        copyButton.setName("notes.copy");
        copyButton.addActionListener(event -> {
            Path selected = list.getSelectedValue();
            if (selected != null) {
                copyAction.accept(selected);
            }
        });
        JButton openButton = new JButton(i18n.get("notes.open"));
        openButton.setName("notes.open");
        openButton.addActionListener(event -> openSelected());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.add(openButton);
        buttons.add(copyButton);

        selectionLabel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        statisticsLabel.setName("notes.stats");
        statisticsLabel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        JPanel details = new JPanel(new BorderLayout());
        details.add(selectionLabel, BorderLayout.NORTH);
        details.add(statisticsLabel, BorderLayout.CENTER);
        details.add(buttons, BorderLayout.SOUTH);

        add(new JScrollPane(list), BorderLayout.CENTER);
        add(details, BorderLayout.SOUTH);
        refreshFiles();
    }

    void refreshFiles() {
        Path previous = list.getSelectedValue();
        List<Path> open = editors.getOpenFiles().stream().filter(MarkdownFiles::isMarkdown).toList();
        updating = true;
        try {
            model.clear();
            open.forEach(model::addElement);
            Path selected = open.contains(previous) ? previous : editors.getSelectedFile()
                    .filter(MarkdownFiles::isMarkdown).filter(open::contains).orElse(open.isEmpty() ? null : open.get(0));
            if (selected != null) {
                list.setSelectedValue(selected, true);
            }
        } finally {
            updating = false;
        }
        selectionChanged();
    }

    void showActive(Path file) {
        refreshFiles();
        if (MarkdownFiles.isMarkdown(file) && model.contains(file)) {
            updating = true;
            try {
                list.setSelectedValue(file, true);
            } finally {
                updating = false;
            }
            selectionChanged();
        }
    }

    private void openSelected() {
        Path selected = list.getSelectedValue();
        if (selected != null) {
            editors.selectFile(selected);
        }
    }

    private void selectionChanged() {
        Path selected = list.getSelectedValue();
        copyButton.setEnabled(selected != null);
        selectionLabel.setText(selected == null ? i18n.get("notes.empty") : selected.getFileName().toString());
        selectionLabel.setToolTipText(selected == null ? null : selected.toString());
        if (selected == null) {
            generation++;
            statisticsLabel.setText(i18n.get("notes.hint"));
            return;
        }
        loadStatistics(selected);
    }

    private void loadStatistics(Path file) {
        long request = ++generation;
        statisticsLabel.setText(i18n.get("notes.stats.loading"));
        new SwingWorker<NoteStatistics, Void>() {
            @Override
            protected NoteStatistics doInBackground() throws Exception {
                return NoteStatistics.count(Files.readString(file));
            }

            @Override
            protected void done() {
                if (request != generation || !file.equals(list.getSelectedValue())) {
                    return;
                }
                try {
                    NoteStatistics stats = get();
                    statisticsLabel.setText(i18n.get("notes.stats",
                            stats.words(), stats.characters(), stats.lines()));
                } catch (CancellationException e) {
                    // Новый выбор имеет собственный request id.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    statisticsLabel.setText(i18n.get("notes.stats.error"));
                } catch (ExecutionException e) {
                    statisticsLabel.setText(i18n.get("notes.stats.error"));
                }
            }
        }.execute();
    }

    private static final class NoteRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            Path file = (Path) value;
            setText(file.getFileName().toString());
            Path parent = file.toAbsolutePath().normalize().getParent();
            setToolTipText(parent == null ? file.toString() : parent.toString());
            return this;
        }
    }
}
