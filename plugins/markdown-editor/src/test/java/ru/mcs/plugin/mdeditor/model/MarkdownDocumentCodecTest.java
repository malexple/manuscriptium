package ru.mcs.plugin.mdeditor.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MarkdownDocumentCodecTest {

    @Test
    void preservesUtf8BomAndCrLf() throws Exception {
        byte[] original = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF,
                't', 'i', 't', 'l', 'e', '\r', '\n', 'x'};

        MarkdownDocumentCodec.DecodedDocument decoded = MarkdownDocumentCodec.decode(original);

        assertThat(decoded.text()).isEqualTo("title\nx");
        assertThat(decoded.properties().lineEnding()).isEqualTo(LineEnding.CRLF);
        assertThat(decoded.properties().bom()).isTrue();
        assertThat(MarkdownDocumentCodec.encode(decoded.text(), decoded.properties())).isEqualTo(original);
    }

    @Test
    void preservesCrOnlyFiles() throws Exception {
        MarkdownDocumentCodec.DecodedDocument decoded = MarkdownDocumentCodec.decode("a\rb\rc".getBytes(StandardCharsets.UTF_8));

        assertThat(decoded.text()).isEqualTo("a\nb\nc");
        assertThat(decoded.properties().lineEnding()).isEqualTo(LineEnding.CR);
        assertThat(new String(MarkdownDocumentCodec.encode(decoded.text(), decoded.properties()), StandardCharsets.UTF_8))
                .isEqualTo("a\rb\rc");
    }

    @Test
    void defaultsToLfWithoutBom() throws Exception {
        MarkdownDocumentCodec.DecodedDocument decoded = MarkdownDocumentCodec.decode("plain".getBytes(StandardCharsets.UTF_8));

        assertThat(decoded.properties()).isEqualTo(DocumentProperties.utf8(LineEnding.LF, false));
        assertThat(MarkdownDocumentCodec.encode(decoded.text(), decoded.properties()))
                .isEqualTo("plain".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsMixedEolInsteadOfSilentlyReformattingWholeFile() throws Exception {
        MarkdownDocumentCodec.DecodedDocument decoded = MarkdownDocumentCodec.decode(
                "a\r\nb\nc\rd".getBytes(StandardCharsets.UTF_8));

        assertThat(decoded.text()).isEqualTo("a\nb\nc\nd");
        assertThat(decoded.properties().lineEnding()).isEqualTo(LineEnding.CRLF);
        assertThat(decoded.properties().mixedLineEndings()).isTrue();
        assertThatThrownBy(() -> MarkdownDocumentCodec.encode(decoded.text(), decoded.properties()))
                .isInstanceOf(CharacterCodingException.class)
                .hasMessageContaining("explicit EOL normalization");
    }

    @Test
    void rejectsMalformedUtf8() {
        assertThatThrownBy(() -> MarkdownDocumentCodec.decode(new byte[]{(byte) 0xC3, 0x28}))
                .isInstanceOf(CharacterCodingException.class);
    }

    @Test
    void preservesUnicodeAndYamlTextExactlyApartFromPhysicalEol() throws Exception {
        String source = "---\r\ntitle: Привет 🙂\r\nunknown: value\r\n---\r\n";

        MarkdownDocumentCodec.DecodedDocument decoded = MarkdownDocumentCodec.decode(source.getBytes(StandardCharsets.UTF_8));

        assertThat(decoded.text()).isEqualTo("---\ntitle: Привет 🙂\nunknown: value\n---\n");
        assertThat(MarkdownDocumentCodec.encode(decoded.text(), decoded.properties()))
                .isEqualTo(source.getBytes(StandardCharsets.UTF_8));
    }
}
