package ru.mcs.spi.action;

/** Позиция действия внутри группы. */
public record Constraints(Anchor anchor, String relativeTo) {

    public enum Anchor { FIRST, LAST, BEFORE, AFTER }

    public static final Constraints FIRST = new Constraints(Anchor.FIRST, null);
    public static final Constraints LAST = new Constraints(Anchor.LAST, null);

    public static Constraints before(String actionId) {
        return new Constraints(Anchor.BEFORE, actionId);
    }

    public static Constraints after(String actionId) {
        return new Constraints(Anchor.AFTER, actionId);
    }
}
