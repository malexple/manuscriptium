# Шаблоны внешних расширений Manuscriptium

Standalone-проект Java 21 + Gradle собирает **два независимых JAR-плагина** для SDK `0.10`:

| Пример | Публичная зависимость | Что демонстрирует |
|---|---|---|
| `src/main` | `ru.mcs.spi.swing` | Универсальное действие, toolbar и tool window без привязки к формату файлов |
| `src/markdownExtension` | `ru.mcs.markdown` | Расширение формата через экспортированный `MarkdownParser` и зависимость `depends-on: [markdown]` |

Каждый JAR содержит собственные `module-info.class` и `plugin.yml`. Это отдельные артефакты, а не один пакет с двумя
точками входа: их можно устанавливать, включать и отключать независимо.

## Сборка

Linux/macOS из корня Manuscriptium:

```bash
./gradlew -p plugin-template clean build
```

Windows:

```bat
.\gradlew.bat -p plugin-template clean build
```

Результат:

```text
plugin-template/build/libs/manuscriptium-plugin-template-0.1.0.jar
plugin-template/build/libs/manuscriptium-markdown-extension-0.1.0.jar
```

Задача `build` включает обычный `jar` и `markdownExtensionJar`. Для сборки только второго примера:

```bash
./gradlew -p plugin-template markdownExtensionJar
```

## Установка и проверка

1. Откройте **Settings → Plugins → Install from disk** и выберите один из JAR.
2. Подтвердите явное предупреждение: Java-плагин имеет полный доступ к JVM и данным пользователя; песочницы нет.
3. Перезапустите приложение.
4. Универсальный шаблон добавляет **Tools → Template**, действие на главную панель и tool window **Template**.
5. Markdown-шаблон добавляет **Tools → Inspect sample Markdown**. Действие получает встроенный `MarkdownParser`,
   разбирает небольшой документ и показывает число AST-диапазонов и заголовков.

Оба примера используют `activation: onDemand`: код загружается при первом обращении к вкладу. Отключение и включение
в Settings применяется после перезапуска. Обновление уже установленного плагина сначала помещается в скрытый
`.pending`-каталог и заменяет прежнюю установку только при следующем запуске.

## Универсальное расширение платформы

Основные файлы:

```text
src/main/java/module-info.java
src/main/java/ru/mcs/plugin/template/TemplatePlugin.java
src/main/resources/plugin.yml
```

`module-info.java` требует только desktop SDK:

```java
module ru.mcs.plugin.template {
    requires ru.mcs.spi.swing;
    opens ru.mcs.plugin.template.icons;
    provides ru.mcs.spi.swing.Plugin with ru.mcs.plugin.template.TemplatePlugin;
}
```

В манифесте `depends-on: []`: расширение не зависит от Markdown и может работать в другой компоновке платформы.
Зависимость Gradle объявлена как `compileOnly`, поэтому классы SDK не упаковываются в JAR.

## Расширение формата Markdown

Файлы второго примера находятся в `src/markdownExtension`. JPMS-зависимость:

```java
module ru.mcs.plugin.markdown.template {
    requires ru.mcs.markdown;
    provides ru.mcs.spi.swing.Plugin with ru.mcs.plugin.markdown.template.MarkdownTemplatePlugin;
}
```

Манифест связывает её с фактически запущенным встроенным модулем:

```yaml
sdk-version: "0.10"
depends-on: [markdown]
```

В коде доступен только экспортированный API:

```java
MarkdownParser parser = context.services().get(MarkdownParser.class);
MarkdownParser.ParseResult result = parser.parse("# Heading\n\nText\n", 1);
```

ID `markdown` относится к встроенному модулю продукта, а не к внешнему JAR. Дополнительный `ModuleLayer` для него не
создаётся: `ru.mcs.markdown` уже находится в boot layer. Если модуль не входит в компоновку или не запустился,
расширение получает понятную ошибку missing dependency. Внешний JAR не может занять ID запущенного built-in модуля.

Зависимости `spi-swing` и `markdown` в Gradle также являются `compileOnly`. Не включайте реализации Manuscriptium,
Flexmark или классы `ru.mcs.*` в распространяемый JAR.

## Свой проект на основе шаблона

1. Скопируйте каталог `plugin-template` в отдельное место.
2. В `settings.gradle` измените `includeBuild('..')` на путь к исходникам Manuscriptium:

   ```groovy
   includeBuild('/path/to/manuscriptium')
   ```

3. Для универсального плагина измените `src/main`; ненужный `markdownExtension` source set, задачу и зависимости можно
   удалить. Для расширения формата сохраните соответствующий source set.
4. Замените ID модуля, пакет и `ru.mcs.plugin.template` на собственные значения.
5. Обновите `plugin.yml`, `messages*.properties`, название артефакта и версию в `build.gradle`.
6. Добавьте реализацию в точку входа и обновите `provides` в `module-info.java`.

Composite build подставляет `ru.mcs:spi-swing:0.1.0-SNAPSHOT` и `ru.mcs:markdown:0.1.0-SNAPSHOT` напрямую из исходников;
предварительная публикация в Maven не нужна. Полное руководство: `docs/plugin-sdk.md` в репозитории Manuscriptium.

## Автоматическая smoke-проверка в packaged app

После `./gradlew :app:jlink` установите оба JAR в отдельный тестовый `--home` и выполните:

```bash
app/build/image/bin/manuscriptium \
  --smoke --home=app/build/external-home \
  --smoke-actions=template.hello,markdown.template.inspect
```

На Windows используйте `app/build/image/bin/manuscriptium.bat`. Smoke активирует все объявленные плагины, выполняет
перечисленные действия, печатает `MCS_SMOKE` и возвращает ненулевой код при ошибке SDK, зависимости, активации или
действия. Успешная строка содержит `smokeActions=[template.hello, markdown.template.inspect]`, `failures={}` и
`builtInFailures={}`.
