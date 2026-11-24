package ru.mcs.search;

import java.nio.file.Path;
import javax.swing.SwingUtilities;
import ru.mcs.spi.project.FullTextIndexFactory;
import ru.mcs.spi.project.ProjectAccess;
import ru.mcs.spi.project.ProjectSearch;
import ru.mcs.spi.project.SearchHit;
import ru.mcs.spi.swing.BuiltInModule;
import ru.mcs.spi.swing.PluginContext;
import ru.mcs.spi.swing.action.ActionManager;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.action.AnActionEvent;
import ru.mcs.spi.swing.ui.ToolWindowManager;
import ru.mcs.spi.swing.ui.ToolWindowSpec;
import ru.mcs.spi.ui.ToolWindowAnchor;

/** Встроенный модуль полнотекстового поиска: Lucene-индекс, панель поиска и действие «Поиск по Project». */
public final class SearchModule implements BuiltInModule {

    static final String TOOL_WINDOW_ID = "core.search";
    static final String ACTION_ID = "core.search.open";

    private ActionManager actions;
    private ToolWindowManager toolWindows;
    private LuceneFullTextIndexFactory factory;
    private SearchPanel panel;

    @Override
    public String id() {
        return "search";
    }

    @Override
    public String i18nBaseName() {
        return "ru/mcs/search/messages/messages";
    }

    @Override
    public void start(PluginContext context) {
        SearchMessages.install(context.i18n());
        ProjectSearch search = context.services().get(ProjectSearch.class);
        ProjectAccess project = context.services().get(ProjectAccess.class);
        actions = context.actions();
        toolWindows = context.toolWindows();

        factory = new LuceneFullTextIndexFactory();
        context.services().register(FullTextIndexFactory.class, factory);

        panel = new SearchPanel(hit -> open(context, project, hit), search);
        toolWindows.register(ToolWindowSpec.of(TOOL_WINDOW_ID, SearchMessages.get("project.search.title"),
                        ToolWindowAnchor.BOTTOM, () -> panel)
                .withIcon(SearchIcons.load("icons/search.svg")));
        SearchPanel target = panel;
        actions.registerAction(ACTION_ID, new AnAction(SearchMessages.get("project.search.action"), null,
                SearchIcons.load("icons/search.svg")) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                toolWindows.get(TOOL_WINDOW_ID).show();
                SwingUtilities.invokeLater(target::focusQuery);
            }
        });
        context.log("Built-in full-text search registered");
    }

    @Override
    public void stop() {
        if (actions != null) {
            actions.unregisterAction(ACTION_ID);
            actions = null;
        }
        if (toolWindows != null) {
            toolWindows.unregister(TOOL_WINDOW_ID);
            toolWindows = null;
        }
        panel = null;
        factory = null;
    }

    private static void open(PluginContext context, ProjectAccess project, SearchHit hit) {
        Path root = project.root().orElse(null);
        if (root == null) {
            return;
        }
        Path file = hit.file().toAbsolutePath().normalize();
        if (!file.startsWith(root)) {
            return;
        }
        context.editors().openFile(file);
        context.statusBar().setMessage(SearchMessages.get("project.search.opened", hit.path()));
    }
}
