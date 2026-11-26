package ru.mcs.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class YamlValidatorTest {

    @Test
    void acceptsValidDocumentsMultipleDocumentsAndUnknownTags() {
        assertThat(YamlValidator.validate("a: 1\nb: [x, y]\n", true)).isNull();
        assertThat(YamlValidator.validate("a: 1\n---\nb: 2\n", true)).isNull();
        assertThat(YamlValidator.validate("a: !Ref Name\nb: !custom [1, 2]\n", true)).isNull();
        assertThat(YamlValidator.validate("", true)).isNull();
    }

    @Test
    void reportsSyntaxErrorsWithAPosition() {
        YamlValidator.Problem unclosed = YamlValidator.validate("a: 1\nb: [\n", true);
        assertThat(unclosed).isNotNull();
        assertThat(unclosed.line()).isGreaterThanOrEqualTo(2);
        assertThat(unclosed.message()).isNotBlank();

        YamlValidator.Problem tab = YamlValidator.validate("a:\n\t- x\n", true);
        assertThat(tab).isNotNull();
        assertThat(tab.line()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void duplicateKeysDependOnStrictness() {
        YamlValidator.Problem strict = YamlValidator.validate("a: 1\na: 2\n", true);
        assertThat(strict).isNotNull();
        assertThat(strict.message()).containsIgnoringCase("duplicate");
        assertThat(YamlValidator.validate("a: 1\na: 2\n", false)).isNull();
    }
}
