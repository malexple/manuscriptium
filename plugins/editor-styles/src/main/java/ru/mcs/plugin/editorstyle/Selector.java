package ru.mcs.plugin.editorstyle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import ru.mcs.spi.editor.style.StyleElement;

/** Разобранный селектор: compounds и combinators, specificity вычисляется один раз. */
final class Selector {

    private record Compound(String tag, String id, Set<String> classes,
                            boolean root, boolean hover, int nthParity) {
        private boolean matches(StyleElement element, boolean hoverState) {
            if (!tag.isEmpty() && !"*".equals(tag) && !tag.equalsIgnoreCase(element.tag())) {
                return false;
            }
            if (!id.isEmpty() && !id.equals(element.id())) {
                return false;
            }
            if (!element.classes().containsAll(classes)) {
                return false;
            }
            if (root && !"root".equals(element.tag())) {
                return false;
            }
            if (hover && !hoverState) {
                return false;
            }
            return nthParity == 0 || (element.childIndex() > 0
                    && (nthParity == 1 ? element.childIndex() % 2 == 1 : element.childIndex() % 2 == 0));
        }
    }

    private record Step(Compound compound, String combinatorToPrevious) {
    }

    private final List<Step> steps;
    private final int specificity;

    private Selector(List<Step> steps, int specificity) {
        this.steps = steps;
        this.specificity = specificity;
    }

    int specificity() {
        return specificity;
    }

    boolean matches(StyleElement target, boolean hover, List<StyleElement> ancestors) {
        if (steps.isEmpty() || !steps.getLast().compound().matches(target, hover)) {
            return false;
        }
        int ancestor = ancestors.size() - 1;
        for (int i = steps.size() - 1; i > 0; i--) {
            String combinator = steps.get(i).combinatorToPrevious();
            Compound wanted = steps.get(i - 1).compound();
            if (">".equals(combinator)) {
                if (ancestor < 0 || !wanted.matches(ancestors.get(ancestor), false)) {
                    return false;
                }
                ancestor--;
            } else {
                while (ancestor >= 0 && !wanted.matches(ancestors.get(ancestor), false)) {
                    ancestor--;
                }
                if (ancestor < 0) {
                    return false;
                }
                ancestor--;
            }
        }
        return true;
    }

    static Selector parse(String source) {
        List<String> tokens = tokens(source);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Empty selector");
        }
        List<Step> steps = new ArrayList<>();
        String combinator = null;
        int specificity = 0;
        for (String token : tokens) {
            if (" ".equals(token) || ">".equals(token)) {
                if (steps.isEmpty() || combinator != null) {
                    throw new IllegalArgumentException("Invalid combinator in " + source);
                }
                combinator = token;
            } else {
                if (!steps.isEmpty() && combinator == null) {
                    throw new IllegalArgumentException("Missing combinator in " + source);
                }
                Compound compound = compound(token);
                specificity += specificity(compound);
                steps.add(new Step(compound, steps.isEmpty() ? null : combinator));
                combinator = null;
            }
        }
        if (combinator != null) {
            throw new IllegalArgumentException("Trailing combinator in " + source);
        }
        return new Selector(List.copyOf(steps), specificity);
    }

    private static List<String> tokens(String source) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parentheses = 0;
        for (int i = 0; i < source.trim().length(); i++) {
            char c = source.trim().charAt(i);
            if (c == '(') {
                parentheses++;
            } else if (c == ')') {
                parentheses--;
            }
            if (parentheses == 0 && Character.isWhitespace(c)) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                if (!result.isEmpty() && !" ".equals(result.getLast()) && !">".equals(result.getLast())) {
                    result.add(" ");
                }
            } else if (parentheses == 0 && c == '>') {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                if (!result.isEmpty() && " ".equals(result.getLast())) {
                    result.removeLast();
                }
                result.add(">");
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result.stream().filter(s -> !s.isEmpty()).toList();
    }

    private static Compound compound(String value) {
        String tag = "";
        String id = "";
        Set<String> classes = new HashSet<>();
        boolean root = false;
        boolean hover = false;
        int nthParity = 0;
        int i = 0;
        if (i < value.length() && (value.charAt(i) == '*' || Character.isLetter(value.charAt(i)))) {
            int start = i++;
            while (i < value.length() && (Character.isLetterOrDigit(value.charAt(i))
                    || value.charAt(i) == '-' || value.charAt(i) == '_')) {
                i++;
            }
            tag = value.substring(start, i).toLowerCase(Locale.ROOT);
        }
        while (i < value.length()) {
            char marker = value.charAt(i++);
            int start = i;
            while (i < value.length() && (Character.isLetterOrDigit(value.charAt(i))
                    || value.charAt(i) == '-' || value.charAt(i) == '_')) {
                i++;
            }
            String name = value.substring(start, i);
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Invalid selector: " + value);
            }
            switch (marker) {
                case '#' -> {
                    if (!id.isEmpty()) {
                        throw new IllegalArgumentException("Multiple ids in " + value);
                    }
                    id = name;
                }
                case '.' -> classes.add(name);
                case ':' -> {
                    switch (name.toLowerCase(Locale.ROOT)) {
                        case "root" -> root = true;
                        case "hover" -> hover = true;
                        case "nth-child" -> {
                            if (i >= value.length() || value.charAt(i) != '(') {
                                throw new IllegalArgumentException("nth-child requires odd/even");
                            }
                            int close = value.indexOf(')', i);
                            if (close < 0) {
                                throw new IllegalArgumentException("Unclosed nth-child");
                            }
                            String argument = value.substring(i + 1, close).trim().toLowerCase(Locale.ROOT);
                            nthParity = switch (argument) {
                                case "odd" -> 1;
                                case "even" -> 2;
                                default -> throw new IllegalArgumentException("Only nth-child(odd/even) supported");
                            };
                            i = close + 1;
                        }
                        default -> throw new IllegalArgumentException("Unsupported pseudo-class: " + name);
                    }
                }
                default -> throw new IllegalArgumentException("Unsupported selector fragment: " + value);
            }
        }
        return new Compound(tag, id, Set.copyOf(classes), root, hover, nthParity);
    }

    private static int specificity(Compound compound) {
        int result = compound.id().isEmpty() ? 0 : 100;
        result += 10 * (compound.classes().size() + (compound.root() ? 1 : 0)
                + (compound.hover() ? 1 : 0) + (compound.nthParity() == 0 ? 0 : 1));
        if (!compound.tag().isEmpty() && !"*".equals(compound.tag())) {
            result++;
        }
        return result;
    }
}
