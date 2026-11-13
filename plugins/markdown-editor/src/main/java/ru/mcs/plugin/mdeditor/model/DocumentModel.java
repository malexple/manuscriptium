package ru.mcs.plugin.mdeditor.model;

/** Источник истины для Canvas и Source. Все обращения выполняются из одного потока владельца редактора. */
public interface DocumentModel {

    CharSequence text();

    int length();

    long version();

    void apply(EditCommand cmd);

    void beginTransaction();

    void endTransaction();

    void addListener(DocumentListener listener);

    void removeListener(DocumentListener listener);

    UndoManager undo();

    DocumentProperties properties();

    Position position(int offset);

    int offset(Position position);

    boolean isModified();

    /** Непрозрачная точка истории, которую можно отметить сохранённой после завершения фоновой записи. */
    SavePoint savePoint();

    void markSaved(SavePoint savePoint);

    default void markSaved() {
        markSaved(savePoint());
    }

    /** Токен имеет смысл только для экземпляра DocumentModel, создавшего эту точку. */
    final class SavePoint {
        private final Object token;

        SavePoint(Object token) {
            this.token = token;
        }

        Object token() {
            return token;
        }
    }
}
