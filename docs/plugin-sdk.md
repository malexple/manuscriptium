# Manuscriptium Plugin SDK 0.9

Руководство по созданию внешнего плагина на Java 21. Готовый проект из этого руководства находится в
[`plugin-template/`](../plugin-template/), расширенный пример — в [`plugins/hello/`](../plugins/hello/). Дополнительные
bundled-примеры: [`plugins/notes/`](../plugins/notes/) (список открытых файлов, действие и фоновая статистика) и
[`plugins/properties/`](../plugins/properties/) (tool window активного редактора, фоновое чтение и безопасная запись).

## 1. Быстрый старт

Требования:

- JDK 21;
- Gradle 8.14.4 или новее в пределах совместимой major-версии;
- исходники Manuscriptium для подключения `ru.mcs.spi` через composite build.

Из корня репозитория выполните:

```bash
./gradlew -p plugin-template clean build
```

На Windows:

```bat
.\gradlew.bat -p plugin-template clean build
```

Результат:

```text
plugin-template/build/libs/manuscriptium-plugin-template-0.1.0.jar
```

Установите его через **Settings → Plugins → Install from disk**, подтвердите предупреждение о правах и перезапустите
приложение. Шаблон добавляет действие в **Tools → Template** и на главную панель, а также tool window **Template**.

После копирования `plugin-template` в другое место измените в `settings.gradle` путь к исходникам Manuscriptium:

```groovy
includeBuild('/path/to/manuscriptium')
```

Composite build подставляет модуль `ru.mcs:spi:0.1.0-SNAPSHOT` напрямую из `spi/`, поэтому предварительная публикация
SDK в Maven-репозиторий не нужна.

## 2. Структура проекта и артефакта

Минимальный проект:

```text
my-plugin/
  settings.gradle
  build.gradle
  src/main/java/
    module-info.java
    ru/example/myplugin/MyPlugin.java
  src/main/resources/
    plugin.yml
    ru/example/myplugin/messages.properties
    ru/example/myplugin/messages_ru.properties
    ru/example/myplugin/icons/my-icon.svg
```

Основной формат распространения — **один JAR с `plugin.yml` в корне**. При локальной установке Manuscriptium также
принимает каталог и ZIP. После установки внешний плагин находится в `<home>/plugins/<id>/`; bundled-плагины приложения
лежат отдельно в `<installation>/system-plugins/` и не изменяются установщиком.

Проверить содержимое пакета:

```bash
jar tf build/libs/my-plugin-0.1.0.jar
```

В выводе обязательно должны быть `plugin.yml` и `module-info.class`. Классы `ru.mcs.spi.*` в JAR попадать не должны.

## 3. Gradle и JPMS

`build.gradle` шаблона:

```groovy
plugins {
    id 'java-library'
}

group = 'ru.example'
version = '0.1.0'

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    compileOnly 'ru.mcs:spi:0.1.0-SNAPSHOT'
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release = 21
}
```

`compileOnly` обязателен: SDK предоставляется приложением. Плагин зависит только от `ru.mcs.spi`, а не от `core` или
`app` — это сохраняет границу API и позволяет загружать его в отдельный `ModuleLayer`.

`module-info.java`:

```java
module ru.example.myplugin {
    requires ru.mcs.spi;

    // Нужен для SVG-ресурсов named-модуля.
    opens ru.example.myplugin.icons;

    provides ru.mcs.spi.Plugin with ru.example.myplugin.MyPlugin;
}
```

Имя модуля должно совпадать с полем `module` в `plugin.yml`. В одном plugin layer должен быть один провайдер
`ru.mcs.spi.Plugin`, который и является точкой входа плагина. Пакет ресурсов пометьте `opens`; чтобы javac считал его
непустым, можно добавить package-private класс-маркер, как в шаблоне.

## 4. `plugin.yml`

Обязательные поля: `id`, `version`, `sdk-version`, `module`. Полный пример:

```yaml
id: ru.example.myplugin
name: My Plugin
version: 0.1.0
sdk-version: "0.9"
module: ru.example.myplugin
depends-on: []
permissions: []
activation: onDemand
i18n: ru/example/myplugin/messages

contributions:
  actionGroups:
    - id: myplugin.group
      text: "%group.myplugin"
      popup: true
      groups: [MainMenu.Tools]
      icon: /ru/example/myplugin/icons/my-icon.svg

  actions:
    - id: myplugin.hello
      text: "%action.hello"
      groups: [myplugin.group, MainToolbar]
      icon: /ru/example/myplugin/icons/my-icon.svg

  toolWindows:
    - id: myplugin.panel
      title: "%toolwindow.panel"
      anchor: right
      show-by-default: false
      icon: /ru/example/myplugin/icons/my-icon.svg

  fileEditors:
    - id: myplugin.editor
      patterns: ["*.myplugin"]
      priority: 10

  settings:
    - id: myplugin.settings
      title: "%settings.title"
      parent-id: null
```

Поля верхнего уровня:

| Поле | Назначение |
|---|---|
| `id` | Уникальный ID плагина. Используйте латинские буквы, цифры, точку, `_` и `-`; первый символ — буква или цифра |
| `name` | Отображаемое имя; по умолчанию равно `id` |
| `version` | Версия самого плагина |
| `sdk-version` | Требуемая версия SDK; сейчас используйте `"0.9"` |
| `module` | Имя JPMS-модуля из `module-info.java` |
| `depends-on` | Список ID обязательных плагинов; по умолчанию `[]` |
| `permissions` | Декларация прав для диалога установки; это не механизм изоляции |
| `activation` | `onStartup` по умолчанию или `onDemand` |
| `i18n` | Базовый путь ResourceBundle без `messages.properties`; необязательное поле |
| `contributions` | Декларативные действия, панели, редакторы и страницы настроек; необязательное поле |

Для SDK `0.x` должны совпасть major и minor: плагин с `sdk-version: "0.9"` совместим с SDK `0.9.0`, а с `0.8` — нет.
Для стабильных major-версий minor плагина не должен быть новее minor SDK. Проверка выполняется до регистрации вкладов
и до загрузки байткода.

## 5. Жизненный цикл

Точка входа реализует `ru.mcs.spi.Plugin`:

```java
public final class MyPlugin implements Plugin {
    private PluginContext context;

    @Override
    public void start(PluginContext context) {
        this.context = context;
        context.log("My plugin started");
    }

    @Override
    public void stop() {
        context = null;
    }
}
```

При `onStartup` плагин активируется после объявления вкладов всех корректных плагинов. При `onDemand` хост сначала
регистрирует proxy-вклады, а `start()` вызывает при первом обращении к любому из них. После `start()` хост вызывает:

- `createAction(id)` для `contributions.actions`;
- `createToolWindowContent(id)` для `contributions.toolWindows`;
- `createFileEditorProvider(id)` для `contributions.fileEditors`;
- `createConfigurable(id)` для `contributions.settings`.

Метод должен вернуть объект для известного ID или `null` для чужого ID. Не регистрируйте программно в `start()` тот же
ID, который уже объявлен в `plugin.yml`: декларативный и программный способы для одного вклада несовместимы.

UI-методы и `AnAction.update()` выполняются на EDT; `update()` должен быть быстрым. Для длительной работы используйте
фоновый поток, а обновление Swing возвращайте на EDT. `stop()` должен освобождать ресурсы, которыми владеет плагин.
Регистрации, выполненные через `PluginContext`, приложение снимает автоматически при остановке плагина.

## 6. Декларативные вклады

### Действия и группы

`actionGroups` создаёт группы, `actions` — атомарные операции. Поля:

| Секция | Обязательные поля | Дополнительные поля |
|---|---|---|
| `actionGroups` | `id`, `text` | `groups`, `popup: true`, `icon` |
| `actions` | `id`, `text` | `groups`, `icon` |

`groups` — список родительских групп. Стандартные ID объявлены в `ActionIds`:

- `MainMenu.File`, `MainMenu.View`, `MainMenu.Tools`, `MainMenu.Help`;
- `MainToolbar`;
- `EditorTabPopup`.

Реализация действия:

```java
@Override
public AnAction createAction(String id) {
    if (!id.equals("myplugin.hello")) {
        return null;
    }
    return new AnAction(context.i18n().get("action.hello")) {
        @Override
        public void actionPerformed(AnActionEvent event) {
            context.notifications().info("My Plugin", "Place: " + event.getPlace());
        }
    };
}
```

Через `event.getData(...)` доступен `DataContext`; текущий файл можно получить через
`EditorDataKeys.ACTIVE_FILE`. В `update()` меняйте только `event.getPresentation()`.

### Tool windows

Обязательные поля: `id`, `title`, `anchor`. `anchor` принимает `left`, `right` или `bottom`.
`show-by-default` по умолчанию `false`; после изменения видимости пользователем приоритет имеет сохранённая раскладка.
Содержимое создаётся лениво в `createToolWindowContent(id)`.

### Редакторы файлов

`fileEditors` требует непустой `patterns`, например `["*.md"]`. Чем больше `priority`, тем выше приоритет; встроенный
текстовый редактор имеет `-100`. `createFileEditorProvider(id)` возвращает `FileEditorProvider`, который создаёт
`FileEditor` для конкретного файла. Редактор обязан корректно сообщать `isModified()`, сохранять файл и освобождать
ресурсы в `dispose()`.

### Настройки

`settings` требует `id` и `title`; `parent-id` задаёт родительскую страницу или отсутствует/`null` для корневой.
`createConfigurable(id)` возвращает `Configurable`. Методы `createComponent`, `isModified`, `apply`, `reset` вызываются
на EDT. `apply()` должен применять модель и может выбросить `ConfigurationException` с понятным пользователю текстом.

## 7. Локализация и иконки

`i18n` указывает базовый путь ресурсов:

```yaml
i18n: ru/example/myplugin/messages
```

Соответствующие файлы:

```text
src/main/resources/ru/example/myplugin/messages.properties
src/main/resources/ru/example/myplugin/messages_ru.properties
```

В манифесте `text: "%action.hello"` и `title: "%panel.title"` означают ключ ResourceBundle. Для литерального текста,
начинающегося с `%`, используйте `%%`. В коде:

```java
String title = context.i18n().get("notification.title");
String message = context.i18n().get("notification.message", fileName);
```

Аргументы форматируются через `MessageFormat`; апострофы в шаблоне удваиваются. Язык применяется при запуске
приложения. Хост читает ресурсы из JAR до активации, поэтому декларативные вклады сразу локализованы.

Иконки задаются абсолютным путём ресурса внутри JAR:

```yaml
icon: /ru/example/myplugin/icons/my-icon.svg
```

Используйте SVG с `stroke="currentColor"` или `fill="currentColor"` для автоматического перекрашивания при смене темы.
Пакет с ресурсами должен быть открыт в `module-info.java`.

## 8. Возможности `PluginContext`

`PluginContext` — единственная точка взаимодействия с приложением:

| Метод | Назначение |
|---|---|
| `pluginId()` | ID текущего плагина |
| `log(message)` | Диагностическое сообщение |
| `actions()` | Программная регистрация действий и размещение в группах |
| `toolWindows()` | Программная регистрация `ToolWindowSpec` |
| `editors()` | Редакторы, открытие/закрытие файлов, закрепление и сохранение |
| `statusBar()` | Виджеты, временные сообщения и индикаторы задач |
| `notifications()` | INFO/WARNING/ERROR-уведомления |
| `messageBus()` | Подписка и публикация событий через `Topic<L>` |
| `services()` | Публикация и получение сервисов по классу/интерфейсу |
| `state()` | Персистентное состояние `PersistentStateComponent` |
| `settings()` | Программная регистрация `ConfigurableProvider` |
| `i18n()` | Локализованные строки плагина |

Все регистрации через scoped-менеджеры контекста принадлежат плагину и автоматически снимаются при его остановке.
Явные `unregister*` нужны только для промежуточного управления объектами во время работы.

### Сервисы и зависимости между плагинами

```java
context.services().register(MyService.class, new MyServiceImpl());
MyService service = context.services().get(MyService.class);
```

`get()` выбрасывает исключение, если сервиса нет; `find()` возвращает `Optional`. Класс или интерфейс сервиса должен
быть виден обоим плагинам. Для обязательной зависимости укажите ID поставщика:

```yaml
depends-on: [ru.example.service-api]
```

Зависимости топологически сортируются, слой зависимости становится родителем слоя потребителя. Отсутствующая,
отключённая, несовместимая или циклическая зависимость блокирует и сам dependent. Duplicate ID между bundled и
внешним источником отклоняется, а не вызывает молчаливую подмену плагина.

### События

```java
context.messageBus().subscribeOnEdt(EditorTopics.EDITOR, event -> {
    // Реакция на событие редактора на EDT.
});
```

Публикация синхронная в потоке издателя. Используйте `subscribeOnEdt` для Swing и `publisher(topic)` для отправки
сообщений через типизированный интерфейс темы.

### Персистентное состояние

```java
public final class MyState implements PersistentStateComponent {
    private int counter;

    @Override
    public synchronized Map<String, Object> getState() {
        return Map.of("counter", counter);
    }

    @Override
    public synchronized void loadState(Map<String, Object> state) {
        Object value = state.get("counter");
        if (value instanceof Number number) {
            counter = number.intValue();
        }
    }
}
```

Регистрация в `start()`:

```java
context.state().register("main", Scope.APPLICATION, state);
```

`register()` сразу загружает сохранённые данные. Состояние может содержать только Map со строковыми ключами, List,
String, Number, Boolean и null. Числа читайте как `Number`. `getState()` должен быть быстрым и потокобезопасным.
`APPLICATION` соответствует каталогу данных приложения, `VAULT` — состоянию vault.

## 9. Фоновая работа и UI

Не блокируйте EDT сетью, файловым вводом-выводом или длительными вычислениями. Для индикации операции используйте:

```java
StatusBarTask task = context.statusBar().startTask("Import");
try {
    // Фоновая работа.
} finally {
    task.finish();
}
```

`startTask()` потокобезопасен. Swing-компоненты создавайте и изменяйте на EDT; для событий шины есть
`subscribeOnEdt`. Останавливайте собственные executor/timer в `stop()`.

## 10. Безопасность и ограничения текущей версии

- Java-плагин имеет **полный доступ к JVM и правам пользователя ОС**. Песочницы нет.
- `permissions` — декларация для предупреждения пользователя, а не enforced capability list.
- Включение, отключение, установка и обновление тяжёлых Java-плагинов применяются через перезапуск.
- Обновление существующего внешнего плагина сохраняется в pending и применяется при следующем запуске до открытия
  старого JAR — это необходимо для Windows.
- Сетевой каталог и сервис загрузки пока не реализованы; установка выполняется с локального JAR/ZIP/каталога.
- Ошибки манифеста, SDK, графа и активации видны в **Settings → Plugins** вместе с исходной причиной.

## 11. Проверка перед публикацией

1. `./gradlew clean build` в проекте плагина завершается без ошибок.
2. JAR содержит `plugin.yml` и `module-info.class` в корне.
3. JAR не содержит `ru/mcs/spi/` и классы самого Manuscriptium.
4. `id` и `module` уникальны и совпадают с манифестом; `sdk-version` совместима с приложением.
5. Все ID из `contributions` обрабатываются соответствующим `create*`-методом.
6. Все `%ключи` существуют в `messages.properties` и `messages_ru.properties`.
7. Все SVG существуют, а resource-пакет открыт в `module-info.java`.
8. Плагин устанавливается через Settings и переживает перезапуск.
9. Для `onDemand` код действительно не активируется до первого использования вклада.
10. Отключение плагина снимает его действия, панели, редакторы, сервисы и состояние корректно.

Для smoke-проверки приложения с внешним плагином можно использовать каталог `--home` и режим `--smoke`: он активирует
все объявленные плагины, печатает `MCS_SMOKE ...` и возвращает ненулевой код при любой ошибке загрузки.
