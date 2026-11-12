# Manuscriptium

Фаза 0: каркас Gradle/JPMS, загрузка плагинов через ModuleLayer, jlink-образ, CI на 3 ОС.

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

## Разработка плагинов

- [Руководство по Plugin SDK 0.9](docs/plugin-sdk.md).
- [Standalone-шаблон JAR-плагина](plugin-template/).
- [Расширенный демо-плагин Hello](plugins/hello/).
