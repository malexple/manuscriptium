package ru.mcs.plugin.mdparser;

import ru.mcs.spi.editor.AstIndex;

/** Тестовый мост к реальному Flexmark AST без production-зависимости редактора от парсера. */
public final class FlexmarkTestAst {

    private FlexmarkTestAst() {
    }

    public static AstIndex parse(String source) {
        return new AstIndex(new FlexmarkMarkdownParser().parse(source, 0));
    }
}
