package ru.mcs.spi.action;

public final class AnActionEvent {

    private final Presentation presentation;
    private final String place;
    private final DataContext dataContext;

    public AnActionEvent(Presentation presentation, String place, DataContext dataContext) {
        this.presentation = presentation;
        this.place = place;
        this.dataContext = dataContext == null ? DataContext.EMPTY : dataContext;
    }

    /** Копия шаблонной презентации: изменения здесь влияют только на текущее отображение. */
    public Presentation getPresentation() {
        return presentation;
    }

    public String getPlace() {
        return place;
    }

    public DataContext getDataContext() {
        return dataContext;
    }

    public <T> T getData(DataKey<T> key) {
        return dataContext.get(key);
    }
}
