package ru.mcs.core.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Небольшой лексер Obsidian-разметки для source-редактора. Он не изменяет Markdown и не пытается быть
 * полным Markdown-парсером: возвращает только ссылки, embeds и callouts с точными UTF-16 offsets.
 */
final class ObsidianSyntax {

    record Link(int start, int end, boolean embed, String target, String fragment, String alias) {
        String navigationTarget() {
            return fragment == null || fragment.isBlank() ? target : target + "#" + fragment;
        }
    }

    record Callout(int start, int end, String type, String title, Character fold) {
    }

    record Result(List<Link> links, List<Callout> callouts) {
        Result {
            links = List.copyOf(links);
            callouts = List.copyOf(callouts);
        }

        static Result empty() {
            return new Result(List.of(), List.of());
        }

        Optional<Link> linkAt(int offset) {
            return links.stream().filter(link -> offset >= link.start() && offset < link.end()).findFirst();
        }
    }

    private ObsidianSyntax() {
    }

    static Result parse(String text) {
        if (text == null || text.isEmpty()) {
            return Result.empty();
        }
        List<Link> links = new ArrayList<>();
        List<Callout> callouts = new ArrayList<>();
        int offset = text.startsWith("\uFEFF") ? 1 : 0;
        int frontMatterOpening = startsFrontMatter(text, offset) ? offset : -1;
        boolean frontMatter = frontMatterOpening >= 0;
        char fence = 0;
        int fenceLength = 0;

        while (offset < text.length()) {
            int end = lineEnd(text, offset);
            String line = text.substring(offset, end);
            if (frontMatter) {
                if (offset != frontMatterOpening && (line.equals("---") || line.equals("...")
                        || line.equals("---\r") || line.equals("...\r"))) {
                    frontMatter = false;
                }
                offset = nextLine(text, end);
                continue;
            }
            String withoutCr = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            FenceMarker marker = fenceMarker(withoutCr);
            if (fence != 0) {
                if (marker != null && marker.character() == fence && marker.length() >= fenceLength
                        && marker.closing()) {
                    fence = 0;
                    fenceLength = 0;
                }
                offset = nextLine(text, end);
                continue;
            }
            if (marker != null) {
                fence = marker.character();
                fenceLength = marker.length();
                offset = nextLine(text, end);
                continue;
            }

            parseCallout(text, offset, end, withoutCr, callouts);
            parseLinks(text, offset, withoutCr, links);
            offset = nextLine(text, end);
        }
        return new Result(links, callouts);
    }

    private static boolean startsFrontMatter(String text, int offset) {
        if (!text.startsWith("---", offset)) {
            return false;
        }
        int after = offset + 3;
        return after < text.length() && (text.charAt(after) == '\n'
                || text.charAt(after) == '\r' && after + 1 < text.length() && text.charAt(after + 1) == '\n');
    }

    private static void parseLinks(String text, int lineStart, String line, List<Link> links) {
        boolean[] code = inlineCode(line);
        int cursor = 0;
        while (cursor + 3 < line.length()) {
            int open = line.indexOf("[[", cursor);
            if (open < 0) {
                return;
            }
            if (code[open] || code[open + 1]) {
                cursor = open + 2;
                continue;
            }
            int close = line.indexOf("]]", open + 2);
            if (close < 0) {
                return;
            }
            if (code[open + 2] || code[close]) {
                cursor = open + 2;
                continue;
            }
            boolean embed = open > 0 && line.charAt(open - 1) == '!' && !code[open - 1];
            int start = lineStart + open - (embed ? 1 : 0);
            String body = line.substring(open + 2, close);
            int pipe = body.indexOf('|');
            String destination = (pipe < 0 ? body : body.substring(0, pipe)).trim();
            String alias = pipe < 0 ? null : body.substring(pipe + 1).trim();
            if (!destination.isBlank()) {
                int hash = destination.indexOf('#');
                String target = (hash < 0 ? destination : destination.substring(0, hash)).trim();
                String fragment = hash < 0 ? null : destination.substring(hash + 1).trim();
                links.add(new Link(start, lineStart + close + 2, embed, target, fragment, alias));
            }
            cursor = close + 2;
        }
    }

    /** Помечает содержимое и ограничители inline-code, включая непарные backtick runs как обычный текст. */
    private static boolean[] inlineCode(String line) {
        boolean[] code = new boolean[line.length()];
        int open = -1;
        int openLength = 0;
        int i = 0;
        while (i < line.length()) {
            if (line.charAt(i) != '`') {
                i++;
                continue;
            }
            int end = i + 1;
            while (end < line.length() && line.charAt(end) == '`') {
                end++;
            }
            int length = end - i;
            if (open < 0) {
                open = i;
                openLength = length;
            } else if (length == openLength) {
                java.util.Arrays.fill(code, open, end, true);
                open = -1;
                openLength = 0;
            }
            i = end;
        }
        return code;
    }

    private static void parseCallout(String text, int lineStart, int lineEnd, String line,
                                     List<Callout> callouts) {
        int i = 0;
        while (i < Math.min(3, line.length()) && line.charAt(i) == ' ') {
            i++;
        }
        if (i >= line.length() || line.charAt(i) != '>') {
            return;
        }
        i++;
        if (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        if (!line.startsWith("[!", i)) {
            return;
        }
        int close = line.indexOf(']', i + 2);
        if (close < 0) {
            return;
        }
        String type = line.substring(i + 2, close).toLowerCase(Locale.ROOT);
        if (type.isBlank() || !type.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '-' || c == '_')) {
            return;
        }
        int cursor = close + 1;
        Character fold = null;
        if (cursor < line.length() && (line.charAt(cursor) == '+' || line.charAt(cursor) == '-')) {
            fold = line.charAt(cursor++);
        }
        String title = cursor < line.length() ? line.substring(cursor).trim() : "";
        int blockEnd = lineEnd;
        int next = nextLine(text, lineEnd);
        while (next < text.length()) {
            int end = lineEnd(text, next);
            String continuation = text.substring(next, end);
            int spaces = 0;
            while (spaces < Math.min(3, continuation.length()) && continuation.charAt(spaces) == ' ') {
                spaces++;
            }
            if (spaces >= continuation.length() || continuation.charAt(spaces) != '>') {
                break;
            }
            blockEnd = end;
            next = nextLine(text, end);
        }
        callouts.add(new Callout(lineStart, blockEnd, type, title, fold));
    }

    private record FenceMarker(char character, int length, boolean closing) {
    }

    private static FenceMarker fenceMarker(String line) {
        int i = 0;
        while (i < Math.min(3, line.length()) && line.charAt(i) == ' ') {
            i++;
        }
        if (i >= line.length() || line.charAt(i) != '`' && line.charAt(i) != '~') {
            return null;
        }
        char character = line.charAt(i);
        int end = i + 1;
        while (end < line.length() && line.charAt(end) == character) {
            end++;
        }
        int length = end - i;
        if (length < 3) {
            return null;
        }
        return new FenceMarker(character, length, line.substring(end).isBlank());
    }

    private static int lineEnd(String text, int start) {
        int newline = text.indexOf('\n', start);
        return newline < 0 ? text.length() : newline;
    }

    private static int nextLine(String text, int lineEnd) {
        return lineEnd < text.length() ? lineEnd + 1 : text.length();
    }
}
