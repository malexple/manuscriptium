package ru.mcs.core.vault;

import java.awt.BorderLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import ru.mcs.core.i18n.Messages;

/** Конкретные YAML-ссылки на выбранный в Files Markdown-файл. */
final class PhysicalLinkSources {

    record Source(ViewDefinition view, List<Integer> path, String title) {
        Source {
            path = List.copyOf(path);
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private PhysicalLinkSources() {
    }

    static List<Source> find(List<ViewDefinition> views, String file) {
        List<Source> result = new ArrayList<>();
        for (ViewDefinition view : views) {
            collect(view, view.entries(), List.of(), view.name(), file, result);
        }
        return List.copyOf(result);
    }

    private static void collect(ViewDefinition view, List<ViewEntry> entries, List<Integer> parentPath,
                                String parentTitle, String file, List<Source> result) {
        for (int i = 0; i < entries.size(); i++) {
            ViewEntry entry = entries.get(i);
            if (!entry.visibility()) {
                continue;
            }
            List<Integer> path = new ArrayList<>(parentPath);
            path.add(i);
            String title = parentTitle + " / " + entry.title();
            if (entry.isNode()) {
                collect(view, entry.children(), path, title, file, result);
            } else if (entry.file().equals(file)) {
                result.add(new Source(view, path, title));
            }
        }
    }

    static Optional<Source> choose(Window owner, List<Source> sources) {
        if (sources.isEmpty()) {
            return Optional.empty();
        }
        if (sources.size() == 1) {
            return Optional.of(sources.getFirst());
        }
        JComboBox<Source> choices = new JComboBox<>(sources.toArray(Source[]::new));
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JLabel(Messages.get("vault.link.move.source")), BorderLayout.NORTH);
        panel.add(choices, BorderLayout.CENTER);
        int answer = JOptionPane.showConfirmDialog(owner, panel, Messages.get("vault.link.move"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION || !(choices.getSelectedItem() instanceof Source source)) {
            return Optional.empty();
        }
        return Optional.of(source);
    }
}
