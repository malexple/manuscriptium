package ru.mcs.core.vault;

import ru.mcs.core.i18n.Messages;

/** Поле Markdown-документа, по которому выполняется полнотекстовый запрос. */
enum VaultSearchScope {
    ALL("vault.search.scope.all"),
    TITLE("vault.search.scope.title"),
    CONTENT("vault.search.scope.content"),
    FRONT_MATTER("vault.search.scope.frontMatter"),
    PATH("vault.search.scope.path"),
    TAGS("vault.search.scope.tags");

    private final String messageKey;

    VaultSearchScope(String messageKey) {
        this.messageKey = messageKey;
    }

    @Override
    public String toString() {
        return Messages.get(messageKey);
    }
}
