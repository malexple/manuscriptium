package ru.mcs.yaml;

/** Комментирует или раскомментирует набор целых строк: если все непустые строки закомментированы — снимает «#». */
final class CommentToggler {

    private CommentToggler() {
    }

    static String toggle(String block) {
        String[] lines = block.split("\n", -1);
        boolean any = false;
        boolean allCommented = true;
        int minIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            any = true;
            int indent = indentOf(line);
            minIndent = Math.min(minIndent, indent);
            if (line.charAt(indent) != '#') {
                allCommented = false;
            }
        }
        if (!any) {
            return block;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i > 0) {
                out.append('\n');
            }
            if (line.isBlank()) {
                out.append(line);
            } else if (allCommented) {
                int indent = indentOf(line);
                String rest = line.substring(indent + 1);
                out.append(line, 0, indent).append(rest.startsWith(" ") ? rest.substring(1) : rest);
            } else {
                out.append(line, 0, minIndent).append("# ").append(line.substring(minIndent));
            }
        }
        return out.toString();
    }

    private static int indentOf(String line) {
        int count = 0;
        while (count < line.length() && (line.charAt(count) == ' ' || line.charAt(count) == '\t')) {
            count++;
        }
        return count;
    }
}
