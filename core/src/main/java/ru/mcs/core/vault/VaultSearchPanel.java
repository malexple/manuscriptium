package ru.mcs.core.vault;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
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
import ru.mcs.core.i18n.Messages;

/** Панель поиска: запрос выполняется вне EDT, generation отбрасывает ответы старого Vault и старых запросов. */
final class VaultSearchPanel extends JPanel {

    @FunctionalInterface
    interface SearchBackend {
        CompletableFuture<List<VaultSearchResult>> search(VaultSearchQuery query);
    }

    private final JTextField queryField = new JTextField();
    private final JComboBox<VaultSearchScope> scopeBox = new JComboBox<>(VaultSearchScope.values());
    private final JComboBox<DraftFilter> draftBox = new JComboBox<>(DraftFilter.values());
    private final JTextField subtreeField = new JTextField(16);
    private final JButton searchButton = new JButton(Messages.get("vault.search.button"));
    private final DefaultListModel<VaultSearchResult> model = new DefaultListModel<>();
    private final JList<VaultSearchResult> results = new JList<>(model);
    private final JLabel status = new JLabel();
    private final Consumer<VaultSearchResult> opener;

    private SearchBackend backend;
    private long generation;

    VaultSearchPanel(Consumer<VaultSearchResult> opener) {
        super(new BorderLayout(0, 8));
        this.opener = opener;
        setBorder(new EmptyBorder(8, 8, 8, 8));

        queryField.setName("vault.search.query");
        queryField.setToolTipText(Messages.get("vault.search.query.tooltip"));
        scopeBox.setName("vault.search.scope");
        draftBox.setName("vault.search.draft");
        subtreeField.setName("vault.search.subtree");
        subtreeField.setToolTipText(Messages.get("vault.search.subtree.tooltip"));
        searchButton.setName("vault.search.submit");
        results.setName("vault.search.results");
        status.setName("vault.search.status");

        JPanel queryRow = new JPanel(new BorderLayout(6, 0));
        queryRow.add(queryField, BorderLayout.CENTER);
        queryRow.add(searchButton, BorderLayout.EAST);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filters.add(new JLabel(Messages.get("vault.search.scope")));
        filters.add(scopeBox);
        filters.add(new JLabel(Messages.get("vault.search.draft.label")));
        filters.add(draftBox);
        filters.add(new JLabel(Messages.get("vault.search.subtree.label")));
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

    void setVault(VaultService service) {
        setBackend(service == null ? null : service::search);
    }

    /** Отдельная точка нуждается только в headless-тестах асинхронной панели. */
    void setBackend(SearchBackend newBackend) {
        backend = newBackend;
        generation++;
        model.clear();
        searchButton.setEnabled(newBackend != null);
        status.setText(Messages.get(newBackend == null ? "vault.search.noVault" : "vault.search.prompt"));
    }

    void focusQuery() {
        queryField.requestFocusInWindow();
    }

    void search() {
        long request = ++generation;
        SearchBackend current = backend;
        if (current == null) {
            status.setText(Messages.get("vault.search.noVault"));
            return;
        }
        searchButton.setEnabled(true);
        VaultSearchQuery query;
        try {
            query = new VaultSearchQuery(queryField.getText(), (VaultSearchScope) scopeBox.getSelectedItem(),
                    subtreeField.getText(), selectedDraft(), VaultSearchQuery.DEFAULT_LIMIT);
        } catch (IllegalArgumentException e) {
            model.clear();
            status.setText(Messages.get("vault.search.invalid", e.getMessage()));
            return;
        }
        if (query.isEmpty()) {
            model.clear();
            status.setText(Messages.get("vault.search.prompt"));
            return;
        }
        searchButton.setEnabled(false);
        status.setText(Messages.get("vault.search.searching"));
        long started = System.nanoTime();
        CompletableFuture<List<VaultSearchResult>> future;
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
                          List<VaultSearchResult> found, Throwable error) {
        if (request != generation || current != backend) {
            return;
        }
        searchButton.setEnabled(true);
        if (error != null) {
            Throwable cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause() : error;
            String detail = cause.getMessage() == null ? cause.toString() : cause.getMessage();
            model.clear();
            status.setText(Messages.get("vault.search.error", detail));
            return;
        }
        model.clear();
        for (VaultSearchResult result : found) {
            model.addElement(result);
        }
        if (!found.isEmpty()) {
            results.setSelectedIndex(0);
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        status.setText(Messages.get("vault.search.results", found.size(), elapsedMs));
    }

    private void searchIfConfigured() {
        if (!queryField.getText().isBlank() || !subtreeField.getText().isBlank() || selectedDraft() != null) {
            search();
        }
    }

    private Boolean selectedDraft() {
        return draftBox.getSelectedItem() instanceof DraftFilter filter ? filter.value : null;
    }

    private void openSelected() {
        VaultSearchResult selected = results.getSelectedValue();
        if (selected != null) {
            opener.accept(selected);
        }
    }

    private enum DraftFilter {
        ANY("vault.search.draft.any", null),
        DRAFT("vault.search.draft.true", Boolean.TRUE),
        PUBLISHED("vault.search.draft.false", Boolean.FALSE);

        private final String messageKey;
        private final Boolean value;

        DraftFilter(String messageKey, Boolean value) {
            this.messageKey = messageKey;
            this.value = value;
        }

        @Override
        public String toString() {
            return Messages.get(messageKey);
        }
    }

    private static final class ResultRenderer implements ListCellRenderer<VaultSearchResult> {
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
        public java.awt.Component getListCellRendererComponent(JList<? extends VaultSearchResult> list,
                                                               VaultSearchResult value, int index,
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
