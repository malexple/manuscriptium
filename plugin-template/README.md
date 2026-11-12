# Шаблон плагина Manuscriptium

Минимальный standalone-проект Java 21 + Gradle. Результат — один JAR с `plugin.yml` в корне.

## Сборка из корня Manuscriptium

Linux/macOS:

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
```

Установка: **Settings → Plugins → Install from disk**, выбрать JAR, подтвердить предупреждение о полном доступе
к JVM и перезапустить приложение. В меню **Tools → Template** и на главной панели появится действие шаблона,
в правой stripe — панель **Template**.

## Свой проект на основе шаблона

1. Скопируйте каталог `plugin-template`.
2. В `settings.gradle` измените `includeBuild('..')` на путь к исходникам Manuscriptium.
3. Замените ID модуля, пакет и `ru.mcs.plugin.template` на собственные значения.
4. Обновите `plugin.yml`, `messages*.properties`, название артефакта и версию в `build.gradle`.
5. Добавьте реализацию в `TemplatePlugin` и при необходимости переименуйте класс; обновите `provides` в
   `module-info.java`.

Зависимость `ru.mcs:spi` остаётся `compileOnly`: классы SDK уже есть у приложения и не должны попадать в JAR
плагина. Полное руководство: `docs/plugin-sdk.md` в репозитории Manuscriptium.
