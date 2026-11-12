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

/** Выбор узла назначения для clone/move через popup; недопустимые цели отфильтрованы заранее. */
final class ViewTargetChooser {

    record Target(List<Integer> path, String title) {
        Target {
            path = List.copyOf(path);
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private ViewTargetChooser() {
    }

    static Optional<List<Integer>> choose(Window owner, ViewDefinition view,
                                           List<Integer> sourcePath, boolean clone) {
        List<Target> targets = targets(view, sourcePath, clone);
        if (targets.isEmpty()) {
            return Optional.empty();
        }
        JComboBox<Target> choices = new JComboBox<>(targets.toArray(Target[]::new));
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JLabel(Messages.get(clone ? "vault.link.clone.target" : "vault.link.move.target")),
                BorderLayout.NORTH);
        panel.add(choices, BorderLayout.CENTER);
        int answer = JOptionPane.showConfirmDialog(owner, panel,
                Messages.get(clone ? "vault.link.clone" : "vault.link.move"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION || !(choices.getSelectedItem() instanceof Target target)) {
            return Optional.empty();
        }
        return Optional.of(target.path());
    }

    static List<Target> targets(ViewDefinition view, List<Integer> sourcePath, boolean clone) {
        List<Target> result = new ArrayList<>();
        Target root = new Target(List.of(), view.name() + " (" + Messages.get("vault.link.target.root") + ")");
        if (allowed(view, sourcePath, root.path(), clone)) {
            result.add(root);
        }
        collect(view, view.entries(), List.of(), "", sourcePath, clone, result);
        return List.copyOf(result);
    }

    private static void collect(ViewDefinition view, List<ViewEntry> entries, List<Integer> parentPath,
                                String parentTitle, List<Integer> sourcePath, boolean clone, List<Target> result) {
        for (int i = 0; i < entries.size(); i++) {
            ViewEntry entry = entries.get(i);
            if (!entry.visibility() || !entry.isNode()) {
                continue;
            }
            List<Integer> path = new ArrayList<>(parentPath);
            path.add(i);
            String title = parentTitle.isEmpty() ? entry.title() : parentTitle + " / " + entry.title();
            if (allowed(view, sourcePath, path, clone)) {
                result.add(new Target(path, title));
            }
            collect(view, entry.children(), path, title, sourcePath, clone, result);
        }
    }

    private static boolean allowed(ViewDefinition view, List<Integer> sourcePath,
                                   List<Integer> targetPath, boolean clone) {
        ViewDefinition probe = new ViewDefinition(view.id(), view.name(), view.file(),
                ViewOperations.copyEntries(view.entries()));
        return ViewOperations.moveOrClone(probe, sourcePath, targetPath, clone);
    }
}
