# Плагин YAML для Manuscriptium

Внешний JAR-плагин: редактор исходного текста для `.yml` и `.yaml` с подсветкой, простой проверкой синтаксиса,
командами «Переключить комментарий», «Переформатировать» и «Проверить синтаксис», страницами настроек
(Editor -> General -> YAML, Editor -> Code Style -> YAML, Editor -> General -> Smart Keys -> YAML)
и группой токенов на странице «Цветовая схема».

## Сборка и установка

```bash
./gradlew -p plugin-yaml clean build
```

Результат: `plugin-yaml/build/libs/manuscriptium-plugin-yaml-0.1.0.jar`.

Вариант 1 — Settings -> Plugins -> Install from disk, выбрать JAR, подтвердить предупреждение, перезапустить приложение.

Вариант 2 — для разработки:

```bash
./gradlew -p plugin-yaml installToDevHome
./gradlew :app:run
```

## Зависимости

Плагин использует SDK `ru.mcs.spi.swing`, модуль `ru.mcs.editor` (Canvas, документ, общие клавиши) и SnakeYAML из
runtime-образа приложения. В JAR они не упаковываются (`compileOnly`).

## Активация

`activation: onStartup`: плагину нужно при запуске зарегистрировать цвета, сочетания клавиш и провайдер редактора.
