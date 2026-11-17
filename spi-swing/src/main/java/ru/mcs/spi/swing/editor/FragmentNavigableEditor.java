package ru.mcs.spi.swing.editor;

/** Редактор структурированного документа, умеющий выбрать секцию или иной локальный fragment. */
public interface FragmentNavigableEditor extends FileEditor {

    void selectFragment(String fragment);
}
