package ru.mcs.core.project;

import ru.mcs.core.i18n.Messages;

/** Поле Markdown-документа, по которому выполняется полнотекстовый запрос. */
enum ProjectSearchScope {
    ALL("project.search.scope.all"),
    TITLE("project.search.scope.title"),
    CONTENT("project.search.scope.content"),
    FRONT_MATTER("project.search.scope.frontMatter"),
    PATH("project.search.scope.path"),
    TAGS("project.search.scope.tags");

    private final String messageKey;

    ProjectSearchScope(String messageKey) {
        this.messageKey = messageKey;
    }

    @Override
    public String toString() {
        return Messages.get(messageKey);
    }
}
