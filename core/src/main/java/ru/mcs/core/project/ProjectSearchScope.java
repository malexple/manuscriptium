package ru.mcs.core.project;

import ru.mcs.core.i18n.Messages;

/** Поле универсального документа, по которому выполняется полнотекстовый запрос. */
enum ProjectSearchScope {
    ALL("project.search.scope.all"),
    TITLE("project.search.scope.title"),
    CONTENT("project.search.scope.content"),
    METADATA("project.search.scope.metadata"),
    PATH("project.search.scope.path"),
    ATTRIBUTES("project.search.scope.attributes");

    private final String messageKey;

    ProjectSearchScope(String messageKey) {
        this.messageKey = messageKey;
    }

    @Override
    public String toString() {
        return Messages.get(messageKey);
    }
}
