# Manuscriptium

Java 21, Gradle/JPMS, Swing + FlatLaf. Реализованы расширяемая оболочка, плагины и фундамент Vault: Markdown-файлы, рекурсивный watcher, пересобираемый SQLite-индекс и физическое дерево заметок.

## Первый запуск 
```
gradle wrapper --gradle-version 8.14.4   # нужен Gradle >= 8.14.4 для плагина jlink 4.x
./gradlew build
./gradlew :app:run                       # окно с панелью из плагина hello
./gradlew :app:devHome :app:jlink          # Hello попадает в image/system-plugins
app/build/image/bin/manuscriptium --smoke --home=app/build/dev-home
```

Bundled-плагины находятся в `system-plugins/` установленного приложения. Внешние устанавливаются в `plugins/`
внутри каталога данных (`--home`), не изменяя файлы установленного приложения.

## Vault

Выберите `File → Open Vault…` и укажите существующий или пустой каталог. Импорт не нужен: все `.md` внутри каталога
остаются источником истины и открываются из панели Vault. Каталоги `.git` и `.manuscriptium` не индексируются.

Служебные данные создаются в `<vault>/.manuscriptium/`:

- `index.db` — пересобираемый SQLite-кэш. При остановленном приложении его можно удалить вместе с `index.db-wal`
  и `index.db-shm`; при следующем открытии индекс будет создан заново из Markdown;
- `state/` — состояние этого Vault, включая открытые вкладки.

Последний Vault открывается автоматически. Внешние создание, изменение и удаление `.md` подхватываются через `WatchService`.

## Разработка плагинов

- [Руководство по Plugin SDK 0.9](docs/plugin-sdk.md).
- [Standalone-шаблон JAR-плагина](plugin-template/).
- [Расширенный демо-плагин Hello](plugins/hello/).
