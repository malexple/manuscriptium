package ru.mcs.markdown.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.mcs.markdown.editor.action.EditorActionIds;
import ru.mcs.markdown.editor.icons.MarkdownIcons;
import ru.mcs.markdown.notes.NotesModule;
import ru.mcs.markdown.properties.PropertiesModule;
import ru.mcs.spi.action.Constraints;
import ru.mcs.spi.swing.PluginContext;
import ru.mcs.spi.swing.action.ActionManager;
import ru.mcs.spi.swing.action.AnAction;
import ru.mcs.spi.swing.action.DefaultActionGroup;
import ru.mcs.spi.swing.settings.ConfigurableProvider;
import ru.mcs.spi.swing.settings.Configurable;
import ru.mcs.spi.swing.ui.ToolWindowSpec;
import ru.mcs.spi.ui.ToolWindowAnchor;

/** Прямая регистрация прежних declarative-вкладов, теперь без plugin.yml и ProxyAction. */
public final class MarkdownContributions {

    private static final String TABLE_MENU = "MarkdownEditor.TableMenu";

    private MarkdownContributions() {
    }

    public static void register(PluginContext ctx, MarkdownEditorModule editor,
                                PropertiesModule properties, NotesModule notes) {
        ActionManager actions = ctx.actions();
        registerGroup(actions, ctx, EditorActionIds.PARAGRAPH_MENU, "menu.paragraph", "MainMenu");
        registerGroup(actions, ctx, EditorActionIds.FORMAT_MENU, "menu.format", "MainMenu");
        registerGroup(actions, ctx, EditorActionIds.INSERT_MENU, "menu.insert", "MainMenu");
        registerGroup(actions, ctx, TABLE_MENU, "menu.table", EditorActionIds.FORMAT_MENU);

        Map<String, List<String>> parents = actionParents();
        for (String id : EditorActionIds.ALL) {
            AnAction action = editor.createAction(id);
            if (action == null) {
                throw new IllegalStateException("Markdown action is not implemented: " + id);
            }
            if (EditorActionIds.TOGGLE_MODE.equals(id)) {
                action.getTemplatePresentation().setIcon(MarkdownIcons.load(
                        "/ru/mcs/markdown/editor/icons/notebook-text.svg"));
            }
            actions.registerAction(id, action);
            for (String parent : parents.get(id)) {
                actions.addToGroup(parent, id, Constraints.LAST);
            }
        }

        AnAction copyWikiLink = notes.createAction("notes.copyWikiLink");
        copyWikiLink.getTemplatePresentation().setIcon(MarkdownIcons.load(
                "/ru/mcs/markdown/notes/icons/copy.svg"));
        actions.registerAction("notes.copyWikiLink", copyWikiLink);
        for (String parent : List.of("MainMenu.Tools", "MainToolbar", "EditorTabPopup")) {
            actions.addToGroup(parent, "notes.copyWikiLink", Constraints.LAST);
        }

        ctx.editors().registerProvider(editor.createFileEditorProvider());
        ctx.settings().register(new ConfigurableProvider() {
            @Override
            public String getId() {
                return "markdown.editor.settings";
            }

            @Override
            public String getDisplayName() {
                return ctx.i18n().get("settings.title");
            }

            @Override
            public Configurable createConfigurable() {
                return editor.createConfigurable();
            }
        });

        ctx.toolWindows().register(ToolWindowSpec
                .of("properties.panel", ctx.i18n().get("toolwindow.properties"), ToolWindowAnchor.RIGHT,
                        () -> properties.createToolWindowContent("properties.panel"))
                .withIcon(MarkdownIcons.load("/ru/mcs/markdown/properties/icons/sliders-horizontal.svg")));
        ctx.toolWindows().register(ToolWindowSpec
                .of("notes.panel", ctx.i18n().get("toolwindow.notes"), ToolWindowAnchor.LEFT,
                        () -> notes.createToolWindowContent("notes.panel"))
                .withIcon(MarkdownIcons.load("/ru/mcs/markdown/notes/icons/notebook-text.svg")));
    }

    private static void registerGroup(ActionManager actions, PluginContext ctx,
                                      String id, String textKey, String parent) {
        actions.registerAction(id, new DefaultActionGroup(ctx.i18n().get(textKey), true));
        actions.addToGroup(parent, id, Constraints.LAST);
    }

    private static Map<String, List<String>> actionParents() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        EditorActionIds.ALL.forEach(id -> result.put(id, new ArrayList<>()));
        add(result, EditorActionIds.TOGGLE_MODE, "MainMenu.View", "MainToolbar", "EditorTabPopup");
        add(result, EditorActionIds.FOCUS_MODE, "MainMenu.View");
        add(result, EditorActionIds.TYPEWRITER_MODE, "MainMenu.View");
        add(result, EditorActionIds.AUTO_PAIR, "MainMenu.View");
        add(result, EditorActionIds.WORD_COUNT, "MainMenu.Tools");
        add(result, EditorActionIds.TOGGLE_CASE, EditorActionIds.FORMAT_MENU);
        add(result, EditorActionIds.DUPLICATE_LINE, EditorActionIds.PARAGRAPH_MENU);
        add(result, EditorActionIds.DELETE_LINE, EditorActionIds.PARAGRAPH_MENU);
        add(result, EditorActionIds.EXTEND_SELECTION, EditorActionIds.PARAGRAPH_MENU);
        add(result, EditorActionIds.PARAGRAPH, EditorActionIds.PARAGRAPH_MENU);
        for (int level = 1; level <= 6; level++) {
            add(result, EditorActionIds.heading(level), EditorActionIds.PARAGRAPH_MENU);
        }
        for (String id : List.of(EditorActionIds.BOLD, EditorActionIds.ITALIC, EditorActionIds.UNDERLINE,
                EditorActionIds.STRIKETHROUGH, EditorActionIds.INLINE_CODE, EditorActionIds.LINK,
                EditorActionIds.CLEAR_FORMATTING)) {
            add(result, id, EditorActionIds.FORMAT_MENU);
        }
        for (String id : List.of(EditorActionIds.BLOCK_QUOTE, EditorActionIds.BULLET_LIST,
                EditorActionIds.ORDERED_LIST, EditorActionIds.TASK_LIST, EditorActionIds.CODE_BLOCK,
                EditorActionIds.TABLE, EditorActionIds.MATH_BLOCK, EditorActionIds.HORIZONTAL_RULE,
                EditorActionIds.IMAGE)) {
            add(result, id, EditorActionIds.INSERT_MENU);
        }
        for (String id : List.of(EditorActionIds.TABLE_ADD_ROW, EditorActionIds.TABLE_REMOVE_ROW,
                EditorActionIds.TABLE_ADD_COLUMN, EditorActionIds.TABLE_REMOVE_COLUMN,
                EditorActionIds.TABLE_ALIGN_LEFT, EditorActionIds.TABLE_ALIGN_CENTER,
                EditorActionIds.TABLE_ALIGN_RIGHT, EditorActionIds.TABLE_COPY_CELL,
                EditorActionIds.TABLE_CLEAR_CELL)) {
            add(result, id, TABLE_MENU);
        }
        Map<String, List<String>> immutable = new LinkedHashMap<>();
        result.forEach((id, groups) -> immutable.put(id, List.copyOf(groups)));
        return Map.copyOf(immutable);
    }

    private static void add(Map<String, List<String>> parents, String id, String... groups) {
        parents.get(id).addAll(List.of(groups));
    }
}
