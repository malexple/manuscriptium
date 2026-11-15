package ru.mcs.spi.editor;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** JDK-only граница JLaTeXMath-плагина; вызывается editor только вне EDT. */
public interface MathRenderingService {

    BufferedImage render(String latex, boolean display, float fontSize, Color color) throws Exception;
}
