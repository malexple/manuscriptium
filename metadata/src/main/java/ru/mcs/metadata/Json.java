package ru.mcs.metadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Небольшой строгий JSON-parser без runtime-зависимостей. */
final class Json {

    private final String source;
    private int offset;
    private int depth;

    private Json(String source) {
        this.source = source;
    }

    static Object parse(String source) {
        Json parser = new Json(source);
        parser.whitespace();
        Object value = parser.value();
        parser.whitespace();
        if (parser.offset != source.length()) {
            throw parser.error("Unexpected trailing content");
        }
        return value;
    }

    private Object value() {
        if (offset >= source.length()) {
            throw error("Expected a value");
        }
        return switch (source.charAt(offset)) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        enter();
        offset++;
        whitespace();
        Map<String, Object> result = new LinkedHashMap<>();
        if (take('}')) {
            leave();
            return result;
        }
        while (true) {
            whitespace();
            if (offset >= source.length() || source.charAt(offset) != '"') {
                throw error("Expected an object key");
            }
            String key = string();
            whitespace();
            expect(':');
            whitespace();
            Object value = value();
            if (result.containsKey(key)) {
                throw error("Duplicate object key: " + key);
            }
            result.put(key, value);
            whitespace();
            if (take('}')) {
                leave();
                return result;
            }
            expect(',');
        }
    }

    private List<Object> array() {
        enter();
        offset++;
        whitespace();
        List<Object> result = new ArrayList<>();
        if (take(']')) {
            leave();
            return result;
        }
        while (true) {
            whitespace();
            result.add(value());
            whitespace();
            if (take(']')) {
                leave();
                return result;
            }
            expect(',');
        }
    }

    private String string() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (offset < source.length()) {
            char ch = source.charAt(offset++);
            if (ch == '"') {
                return result.toString();
            }
            if (ch < 0x20) {
                throw error("Control character in a string");
            }
            if (ch != '\\') {
                result.append(ch);
                continue;
            }
            if (offset >= source.length()) {
                throw error("Unfinished escape sequence");
            }
            char escaped = source.charAt(offset++);
            switch (escaped) {
                case '"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> result.append(unicode());
                default -> throw error("Unknown escape sequence: \\" + escaped);
            }
        }
        throw error("Unterminated string");
    }

    private char unicode() {
        if (offset + 4 > source.length()) {
            throw error("Unfinished Unicode escape");
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(source.charAt(offset++), 16);
            if (digit < 0) {
                throw error("Invalid Unicode escape");
            }
            value = value * 16 + digit;
        }
        return (char) value;
    }

    private Object literal(String text, Object value) {
        if (!source.startsWith(text, offset)) {
            throw error("Expected " + text);
        }
        offset += text.length();
        return value;
    }

    private Object number() {
        int start = offset;
        take('-');
        if (take('0')) {
            if (offset < source.length() && Character.isDigit(source.charAt(offset))) {
                throw error("Leading zero in a number");
            }
        } else {
            digits();
        }
        boolean decimal = false;
        if (take('.')) {
            decimal = true;
            digits();
        }
        if (offset < source.length() && (source.charAt(offset) == 'e' || source.charAt(offset) == 'E')) {
            decimal = true;
            offset++;
            if (offset < source.length() && (source.charAt(offset) == '+' || source.charAt(offset) == '-')) {
                offset++;
            }
            digits();
        }
        if (start == offset) {
            throw error("Expected a value");
        }
        String number = source.substring(start, offset);
        try {
            if (decimal) {
                return Double.valueOf(number);
            }
            return Long.valueOf(number);
        } catch (NumberFormatException e) {
            throw error("Invalid number: " + number);
        }
    }

    private void digits() {
        int start = offset;
        while (offset < source.length() && Character.isDigit(source.charAt(offset))) {
            offset++;
        }
        if (start == offset) {
            throw error("Expected a digit");
        }
    }

    private void whitespace() {
        while (offset < source.length()) {
            char ch = source.charAt(offset);
            if (ch != ' ' && ch != '\n' && ch != '\r' && ch != '\t') {
                return;
            }
            offset++;
        }
    }

    private boolean take(char expected) {
        if (offset < source.length() && source.charAt(offset) == expected) {
            offset++;
            return true;
        }
        return false;
    }

    private void expect(char expected) {
        if (!take(expected)) {
            throw error("Expected '" + expected + "'");
        }
    }

    private void enter() {
        if (++depth > 256) {
            throw error("JSON is nested too deeply");
        }
    }

    private void leave() {
        depth--;
    }

    private MetadataException error(String message) {
        return new MetadataException(message + " at character " + offset);
    }
}
