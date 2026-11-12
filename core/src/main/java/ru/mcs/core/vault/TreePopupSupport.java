package ru.mcs.core.vault;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Function;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.tree.TreePath;

/** Единый popup для левого и правого щелчка; задержка отличает одиночный click от открытия double-click. */
final class TreePopupSupport {

    private TreePopupSupport() {
    }

    static void install(JTree tree, Function<TreePath, JPopupMenu> popupFactory) {
        final class Listener extends MouseAdapter {
            private final Timer leftTimer;
            private MouseEvent pendingLeft;

            private Listener() {
                leftTimer = new Timer(230, e -> {
                    if (pendingLeft != null) {
                        selectAndShow(tree, popupFactory, pendingLeft);
                        pendingLeft = null;
                    }
                });
                leftTimer.setRepeats(false);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    selectAndShow(tree, popupFactory, e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    selectAndShow(tree, popupFactory, e);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (e.getClickCount() > 1) {
                    pendingLeft = null;
                    leftTimer.stop();
                } else {
                    pendingLeft = e;
                    leftTimer.restart();
                }
            }
        }
        Listener listener = new Listener();
        tree.addMouseListener(listener);
    }

    private static void selectAndShow(JTree tree, Function<TreePath, JPopupMenu> popupFactory, MouseEvent e) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }
        tree.setSelectionPath(path);
        JPopupMenu popup = popupFactory.apply(path);
        if (popup != null) {
            popup.show(tree, e.getX(), e.getY());
        }
    }
}
