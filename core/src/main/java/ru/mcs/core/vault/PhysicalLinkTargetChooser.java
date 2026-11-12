package ru.mcs.core.vault;

import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import ru.mcs.core.i18n.Messages;

/** Цель для ссылки из Files: узел любого view либо корень нового view. */
final class PhysicalLinkTargetChooser {

    record Target(ViewDefinition view, List<Integer> path, String title) {
        Target {
            path = List.copyOf(path);
        }

        @Override
        public String toString() {
            return title;
        }
    }

    record Selection(String viewId, String newViewName, List<Integer> targetPath) {
        Selection {
            targetPath = List.copyOf(targetPath);
        }
    }

    private PhysicalLinkTargetChooser() {
    }

    static Optional<Selection> choose(Window owner, List<ViewDefinition> views, String file) {
        return choose(owner, targets(views, file), true);
    }

    static Optional<Selection> chooseMove(Window owner, List<ViewDefinition> views,
                                          PhysicalLinkSources.Source source) {
        return choose(owner, moveTargets(views, source), false);
    }

    private static Optional<Selection> choose(Window owner, List<Target> existingTargets, boolean clone) {
        List<Target> targets = new ArrayList<>(existingTargets);
        Target newView = new Target(null, List.of(), Messages.get("vault.link.newView"));
        targets.add(newView);

        JComboBox<Target> choices = new JComboBox<>(targets.toArray(Target[]::new));
        JTextField name = new JTextField(Messages.get("vault.link.newView.defaultName"), 24);
        choices.addActionListener(e -> name.setEnabled(choices.getSelectedItem() == newView));
        choices.setSelectedIndex(0);
        name.setEnabled(choices.getSelectedItem() == newView);

        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel(Messages.get(clone ? "vault.link.clone.target" : "vault.link.move.target")));
        panel.add(choices);
        panel.add(new JLabel(Messages.get("vault.link.newView.name")));
        panel.add(name);

        while (true) {
            int answer = JOptionPane.showConfirmDialog(owner, panel,
                    Messages.get(clone ? "vault.link.clone" : "vault.link.move"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer != JOptionPane.OK_OPTION || !(choices.getSelectedItem() instanceof Target target)) {
                return Optional.empty();
            }
            if (target.view() != null) {
                return Optional.of(new Selection(target.view().id(), null, target.path()));
            }
            String viewName = name.getText().trim();
            if (!viewName.isEmpty()) {
                return Optional.of(new Selection(null, viewName, List.of()));
            }
            JOptionPane.showMessageDialog(owner, Messages.get("vault.link.newView.name.required"),
                    Messages.get(clone ? "vault.link.clone" : "vault.link.move"), JOptionPane.WARNING_MESSAGE);
        }
    }

    static List<Target> targets(List<ViewDefinition> views, String file) {
        List<Target> result = new ArrayList<>();
        for (ViewDefinition view : views) {
            Target root = new Target(view, List.of(), view.name() + " (" + Messages.get("vault.link.target.root") + ")");
            if (allowed(view, file, root.path())) {
                result.add(root);
            }
            collect(view, view.entries(), List.of(), view.name(), file, result);
        }
        return List.copyOf(result);
    }

    static List<Target> moveTargets(List<ViewDefinition> views, PhysicalLinkSources.Source source) {
        List<Target> result = new ArrayList<>();
        for (ViewTargetChooser.Target target : ViewTargetChooser.targets(source.view(), source.path(), false)) {
            String title = target.path().isEmpty() ? target.title() : source.view().name() + " / " + target.title();
            result.add(new Target(source.view(), target.path(), title));
        }
        for (ViewDefinition view : views) {
            if (!view.id().equals(source.view().id())) {
                result.addAll(targets(List.of(view), fileAt(source)));
            }
        }
        return List.copyOf(result);
    }

    private static String fileAt(PhysicalLinkSources.Source source) {
        ViewEntry entry = ViewOperations.entryAt(source.view().entries(), source.path());
        return entry == null || entry.isNode() ? "" : entry.file();
    }

    private static void collect(ViewDefinition view, List<ViewEntry> entries, List<Integer> parentPath,
                                String parentTitle, String file, List<Target> result) {
        for (int i = 0; i < entries.size(); i++) {
            ViewEntry entry = entries.get(i);
            if (!entry.visibility() || !entry.isNode()) {
                continue;
            }
            List<Integer> path = new ArrayList<>(parentPath);
            path.add(i);
            String title = parentTitle + " / " + entry.title();
            if (allowed(view, file, path)) {
                result.add(new Target(view, path, title));
            }
            collect(view, entry.children(), path, title, file, result);
        }
    }

    private static boolean allowed(ViewDefinition view, String file, List<Integer> targetPath) {
        ViewDefinition probe = new ViewDefinition(view.id(), view.name(), view.file(),
                ViewOperations.copyEntries(view.entries()));
        return ViewOperations.addLink(probe, file, targetPath);
    }
}
