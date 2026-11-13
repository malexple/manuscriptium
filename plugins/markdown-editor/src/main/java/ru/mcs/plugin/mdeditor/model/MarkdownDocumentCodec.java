package ru.mcs.plugin.mdeditor.model;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Строгий UTF-8 codec с сохранением BOM и исходного перевода строк. */
public final class MarkdownDocumentCodec {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    public record DecodedDocument(String text, DocumentProperties properties) {
        public DecodedDocument {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(properties, "properties");
        }
    }

    private MarkdownDocumentCodec() {
    }

    public static DecodedDocument decode(byte[] bytes) throws CharacterCodingException {
        Objects.requireNonNull(bytes, "bytes");
        boolean bom = startsWithBom(bytes);
        ByteBuffer input = ByteBuffer.wrap(bytes, bom ? UTF8_BOM.length : 0, bytes.length - (bom ? UTF8_BOM.length : 0));
        String decoded = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(input)
                .toString();
        NormalizedText normalized = normalize(decoded);
        return new DecodedDocument(normalized.text(),
                DocumentProperties.utf8(normalized.lineEnding(), bom, normalized.mixedLineEndings()));
    }

    public static byte[] encode(CharSequence text, DocumentProperties properties) throws CharacterCodingException {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(properties, "properties");
        if (properties.mixedLineEndings()) {
            throw new MixedLineEndingsException();
        }
        String normalized = text.toString();
        String physical = properties.lineEnding() == LineEnding.LF
                ? normalized : normalized.replace("\n", properties.lineEnding().sequence());
        byte[] content = physical.getBytes(properties.charset());
        if (!properties.bom()) {
            return content;
        }
        return ByteBuffer.allocate(UTF8_BOM.length + content.length).put(UTF8_BOM).put(content).array();
    }

    private static boolean startsWithBom(byte[] bytes) {
        return bytes.length >= UTF8_BOM.length
                && bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2];
    }

    private static NormalizedText normalize(String text) {
        StringBuilder result = new StringBuilder(text.length());
        LineEnding detected = null;
        boolean mixed = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            LineEnding current = null;
            if (ch == '\r') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    current = LineEnding.CRLF;
                    i++;
                } else {
                    current = LineEnding.CR;
                }
                result.append('\n');
            } else {
                if (ch == '\n') {
                    current = LineEnding.LF;
                }
                result.append(ch);
            }
            if (current != null) {
                if (detected == null) {
                    detected = current;
                } else if (detected != current) {
                    mixed = true;
                }
            }
        }
        return new NormalizedText(result.toString(), detected == null ? LineEnding.LF : detected, mixed);
    }

    private record NormalizedText(String text, LineEnding lineEnding, boolean mixedLineEndings) {
    }

    private static final class MixedLineEndingsException extends CharacterCodingException {
        @Override
        public String getMessage() {
            return "Mixed line endings require explicit EOL normalization before saving";
        }
    }
}
