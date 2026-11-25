package ru.mcs.editor.view;

import java.awt.Color;

/** Цвет токена для светлого или тёмного фона; {@code null} — основной цвет текста. */
@FunctionalInterface
public interface TokenColors {

    Color color(String token, boolean darkBackground);
}
