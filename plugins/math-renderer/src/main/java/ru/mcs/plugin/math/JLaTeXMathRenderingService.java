package ru.mcs.plugin.math;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;
import ru.mcs.spi.swing.editor.MathRenderingService;

final class JLaTeXMathRenderingService implements MathRenderingService {

    @Override
    public BufferedImage render(String latex, boolean display, float fontSize, Color color) {
        TeXFormula formula = new TeXFormula(latex);
        formula.setColor(color);
        TeXIcon icon = formula.createTeXIcon(display ? TeXConstants.STYLE_DISPLAY : TeXConstants.STYLE_TEXT,
                Math.max(8f, fontSize));
        BufferedImage image = new BufferedImage(Math.max(1, icon.getIconWidth()), Math.max(1, icon.getIconHeight()),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            icon.paintIcon(null, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
