package ru.mcs.spi.editor.style;

/** CSS-длина. NUMBER используется для безразмерного line-height. */
public record CssLength(double value, Unit unit) {

    public enum Unit {
        PX, EM, REM, PERCENT, PT, NUMBER
    }

    public static final CssLength ZERO = new CssLength(0, Unit.PX);

    public CssLength {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("CSS length must be finite");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
    }

    public static CssLength px(double value) {
        return new CssLength(value, Unit.PX);
    }

    /** reference — размер родительского шрифта или контейнера для %, root — размер корневого шрифта. */
    public double toPx(double reference, double root) {
        return switch (unit) {
            case PX -> value;
            case EM, NUMBER -> value * reference;
            case REM -> value * root;
            case PERCENT -> value * reference / 100.0;
            case PT -> value * 96.0 / 72.0;
        };
    }
}
