package ru.mcs.yaml;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.nodes.Node;

/** Простая проверка: разбор всех документов потока и, по желанию, поиск повторяющихся ключей. */
final class YamlValidator {

    static final int MAX_CHARS = 1_000_000;

    record Problem(int line, int column, String message) {
    }

    private YamlValidator() {
    }

    /** @return null, если текст разбирается без ошибок */
    static Problem validate(String text, boolean strictDuplicateKeys) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(!strictDuplicateKeys);
        Yaml yaml = new Yaml(new LenientConstructor(options));
        try {
            yaml.loadAll(text).forEach(document -> { });
            return null;
        } catch (MarkedYAMLException error) {
            Mark mark = error.getProblemMark() != null ? error.getProblemMark() : error.getContextMark();
            String problem = error.getProblem() != null ? error.getProblem() : String.valueOf(error.getMessage());
            return new Problem(mark == null ? 1 : mark.getLine() + 1, mark == null ? 1 : mark.getColumn() + 1, problem);
        } catch (RuntimeException error) {
            String message = String.valueOf(error.getMessage()).lines().findFirst().orElse("Invalid YAML");
            return new Problem(1, 1, message);
        }
    }

    /** Неизвестные теги (например, !Ref) не считаются ошибкой: содержимое просто пропускается. */
    private static final class LenientConstructor extends SafeConstructor {

        LenientConstructor(LoaderOptions options) {
            super(options);
            yamlConstructors.put(null, new AbstractConstruct() {
                @Override
                public Object construct(Node node) {
                    return null;
                }
            });
        }
    }
}
