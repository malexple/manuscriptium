package ru.mcs.spi.action;

/** Контекст, в котором выполняется действие. Реализации появятся вместе с областью редактора. */
public interface DataContext {

    DataContext EMPTY = new DataContext() {
        @Override
        public <T> T get(DataKey<T> key) {
            return null;
        }
    };

    <T> T get(DataKey<T> key);
}
