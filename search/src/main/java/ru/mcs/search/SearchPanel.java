package ru.mcs.search;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import ru.mcs.spi.project.ProjectSearch;
import ru.mcs.spi.project.SearchHit;
import ru.mcs.spi.project.SearchQuery;
import ru.mcs.spi.project.SearchScope;

/** Панель поиска: запрос выполняется вне EDT, generation отбрасывает ответы старого Project и старых запросов. */
final class SearchPanel extends JPanel {

    @FunctionalInterface
    interface SearchBackend {
        CompletableFuture<List<SearchHit>> search(SearchQuery query);
    }

    private final JTextField queryField = new JTextField();
    private final JComboBox<SearchScope> scopeBox = new JComboBox<>(SearchScope.values());
    private final JComboBox<DraftFilter> draftBox = new JComboBox<>(DraftFilter.values());
    private final JTextField subtreeField = new JTextField(16);
    private final JButton searchButton = new JButton(SearchMessages.get("project.search.button"));
    private final DefaultListModel<SearchHit> model = new DefaultListModel<>();
    private final JList<SearchHit> results = new JList<>(model);
    private final JLabel status = new JLabel();
    private final Consumer<SearchHit> opener;

    private SearchBackend backend;
    private long generation;

    SearchPanel(Consumer<SearchHit> opener) {
        super(new BorderLayout(0, 8));
        this.opener = opener;
        setBorder(new EmptyBorder(8, 8, 8, 8));

        queryField.setName("project.search.query");
        queryField.setToolTipText(SearchMessages.get("project.search.query.tooltip"));
        scopeBox.setName("project.search.scope");
        scopeBox.setRenderer(new ScopeRenderer());
        draftBox.setName("project.search.draft");
        subtreeField.setName("project.search.subtree");
        subtreeField.setToolTipText(SearchMessages.get("project.search.subtree.tooltip"));
        searchButton.setName("project.search.submit");
        results.setName("project.search.results");
        status.setName("project.search.status");

        JPanel queryRow = new JPanel(new BorderLayout(6, 0));
        queryRow.add(queryField, BorderLayout.CENTER);
        queryRow.add(searchButton, BorderLayout.EAST);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filters.add(new JLabel(SearchMessages.get("project.search.scope")));
        filters.add(scopeBox);
        filters.add(new JLabel(SearchMessages.get("project.search.draft.label")));
        filters.add(draftBox);
        filters.add(new JLabel(SearchMessages.get("project.search.subtree.label")));
        filters.add(subtreeField);

        JPanel controls = new JPanel(new BorderLayout(0, 6));
        controls.add(queryRow, BorderLayout.NORTH);
        controls.add(filters, BorderLayout.SOUTH);
        add(controls, BorderLayout.NORTH);

        results.setCellRenderer(new ResultRenderer());
        results.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openSelected();
                }
            }
        });
        results.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "mcs.openSearchResult");
        results.getActionMap().put("mcs.openSearchResult", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openSelected();
            }
        });
        add(new JScrollPane(results), BorderLayout.CENTER);
        status.setBorder(new EmptyBorder(2, 2, 0, 2));
        add(status, BorderLayout.SOUTH);

        queryField.addActionListener(e -> search());
        subtreeField.addActionListener(e -> search());
        searchButton.addActionListener(e -> search());
        scopeBox.addActionListener(e -> searchIfConfigured());
        draftBox.addActionListener(e -> searchIfConfigured());
        setBackend(null);
    }

    SearchPanel(Consumer<SearchHit> opener, ProjectSearch search) {
        this(opener);
        projectChanged(search);
        search.addChangeListener(() -> projectChanged(search));
    }

    private void projectChanged(ProjectSearch search) {
        setBackend(search.available() ? search::search : null);
    }

    /** Отдельная точка нуждается только в headless-тестах асинхронной панели. */
    void setBackend(SearchBackend newBackend) {
        backend = newBackend;
        generation++;
        model.clear();
        searchButton.setEnabled(newBackend != null);
        status.setText(SearchMessages.get(newBackend == null ? "project.search.noProject" : "project.search.prompt"));
    }

    void focusQuery() {
        queryField.requestFocusInWindow();
    }

    void search() {
        long request = ++generation;
        SearchBackend current = backend;
        if (current == null) {
            status.setText(SearchMessages.get("project.search.noProject"));
            return;
        }
        searchButton.setEnabled(true);
        SearchQuery query;
        try {
            query = new SearchQuery(queryField.getText(), (SearchScope) scopeBox.getSelectedItem(),
                    subtreeField.getText(), selectedAttributeFilters(), SearchQuery.DEFAULT_LIMIT);
        } catch (IllegalArgumentException e) {
            model.clear();
            status.setText(SearchMessages.get("project.search.invalid", e.getMessage()));
            return;
        }
        if (query.isEmpty()) {
            model.clear();
            status.setText(SearchMessages.get("project.search.prompt"));
            return;
        }
        searchButton.setEnabled(false);
        status.setText(SearchMessages.get("project.search.searching"));
        long started = System.nanoTime();
        CompletableFuture<List<SearchHit>> future;
        try {
            future = current.search(query);
        } catch (RuntimeException e) {
            complete(request, current, started, null, e);
            return;
        }
        future.whenComplete((found, error) -> SwingUtilities.invokeLater(
                () -> complete(request, current, started, found, error)));
    }

    private void complete(long request, SearchBackend current, long started,
                          List<SearchHit> found, Throwable error) {
        if (request != generation || current != backend) {
            return;
        }
        searchButton.setEnabled(true);
        if (error != null) {
            Throwable cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause() : error;
            String detail = cause.getMessage() == null ? cause.toString() : cause.getMessage();
            model.clear();
            status.setText(SearchMessages.get("project.search.error", detail));
            return;
        }
        model.clear();
        for (SearchHit result : found) {
            model.addElement(result);
        }
        if (!found.isEmpty()) {
            results.setSelectedIndex(0);
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        status.setText(SearchMessages.get("project.search.results", found.size(), elapsedMs));
    }

    private void searchIfConfigured() {
        if (!queryField.getText().isBlank() || !subtreeField.getText().isBlank() || !selectedAttributeFilters().isEmpty()) {
            search();
        }
    }

    private Map<String, String> selectedAttributeFilters() {
        if (draftBox.getSelectedItem() instanceof DraftFilter filter && filter.value != null) {
            return Map.of("draft", filter.value.toString());
        }
        return Map.of();
    }

    private void openSelected() {
        SearchHit selected = results.getSelectedValue();
        if (selected != null) {
            opener.accept(selected);
        }
    }

    private static final class ScopeRenderer extends DefaultListCellRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                               boolean selected, boolean focused) {
            Object shown = value instanceof SearchScope scope
                    ? SearchMessages.get("project.search.scope." + scope.name().toLowerCase(java.util.Locale.ROOT))
                    : value;
            return super.getListCellRendererComponent(list, shown, index, selected, focused);
        }
    }

    private enum DraftFilter {
        ANY("project.search.draft.any", null),
        DRAFT("project.search.draft.true", Boolean.TRUE),
        PUBLISHED("project.search.draft.false", Boolean.FALSE);

        private final String messageKey;
        private final Boolean value;

        DraftFilter(String messageKey, Boolean value) {
            this.messageKey = messageKey;
            this.value = value;
        }

        @Override
        public String toString() {
            return SearchMessages.get(messageKey);
        }
    }

    private static final class ResultRenderer implements ListCellRenderer<SearchHit> {
        private final JPanel panel = new JPanel(new BorderLayout(0, 2));
        private final JLabel title = new JLabel();
        private final JLabel path = new JLabel();
        private final JLabel snippet = new JLabel();

        private ResultRenderer() {
            panel.setBorder(new EmptyBorder(5, 7, 5, 7));
            JPanel labels = new JPanel(new BorderLayout(0, 2));
            labels.add(path, BorderLayout.NORTH);
            labels.add(snippet, BorderLayout.SOUTH);
            panel.add(title, BorderLayout.NORTH);
            panel.add(labels, BorderLayout.SOUTH);
        }

        @Override
        public java.awt.Component getListCellRendererComponent(JList<? extends SearchHit> list,
                                                               SearchHit value, int index,
                                                               boolean selected, boolean focused) {
            title.setText(value.title());
            path.setText(value.path());
            snippet.setText(value.snippet());
            panel.setToolTipText(value.path());
            Color background = selected ? list.getSelectionBackground() : list.getBackground();
            Color foreground = selected ? list.getSelectionForeground() : list.getForeground();
            Color secondary = selected ? foreground : UIManager.getColor("Label.disabledForeground");
            panel.setBackground(background);
            for (java.awt.Component component : new java.awt.Component[]{title, path, snippet, panel.getComponent(1)}) {
                component.setBackground(background);
            }
            title.setForeground(foreground);
            path.setForeground(secondary);
            snippet.setForeground(secondary);
            panel.setOpaque(true);
            ((JPanel) panel.getComponent(1)).setOpaque(true);
            return panel;
        }
    }
}
