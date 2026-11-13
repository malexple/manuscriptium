package ru.mcs.plugin.mdeditor.model;

/** Единственный набор команд, которыми может быть изменён документ. */
public sealed interface EditCommand permits Insert, Delete, Replace, Composite {
}
