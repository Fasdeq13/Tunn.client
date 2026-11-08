# Tunn: карта файлов

Android VPN-клиент. Логика на Rust и C, Kotlin только для интерфейса и VpnService. Пакет `tunn.client`, нативная библиотека `tunn_service`, ядро `tunn-backend`. Разработка в репозитории Fasdeq13/FasdeqVPN, плагины в Fasdeq13/Tunn-plugins.

## Корень
- `tools/pack.py`, `tools/test_pack.py` сборка и подпись пакетов `.Update` / `.fix`.
- `build.sh` собирает всё (Rust, ядра, APK/AAB); версия в переменной `VERSION`.
- `shell.nix` окружение сборки на NixOS.
- `fdroid/tunn.client.yml`, `fdroid/README.md` метаданные F-Droid.
- `LICENSE`, `.gitignore`.

## core/ (крейт tunn-backend, чистый Rust)
- `lib.rs` список модулей.
- `model.rs` типы сервера и результатов.
- `links.rs` разбор ссылок vless/vmess/trojan/ss/wg/hy2 и других.
- `importer.rs` разбор подписок и конфигов.
- `ping.rs` замеры TCP/UDP/ICMP.
- `proc.rs` запуск и контроль внешних процессов ядер.
- `engine.rs` политика переподключения, выбор запасного сервера, журнал, заголовки подписок.
- `update.rs` формат обновлений `.Update` и `.fix`: имя файла, проверка манифеста, безопасные пути.

## android-native/ (крейт tunn-service, JNI cdylib)
- `src/lib.rs` JNI-функции для Kotlin.
- `src/api.rs` обвязка над tunn-backend.
- `src/plugin.rs` загрузчик протокольных плагинов (.so).
- `src/uihost.rs` хост интерфейсов-плагинов (.so на Rust).
- `tests/plugin_abi.rs` тест ABI плагинов.

## native-c/ (библиотека libfqc, пока не подключена к сборке)
- `include/fqc.h` заголовок.
- `src/b64.c` base64; `src/cidr.c` проверка CIDR; `src/link.c` разбор ссылок; `src/sha256.c` SHA-256; `src/jni.c` JNI-обёртки.
- `test/test.c` тесты; `CMakeLists.txt`, `Makefile`, `README.md` сборка.

## android/ (Gradle)
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` настройки проекта.
- `app/build.gradle.kts` версия, applicationId, зависимости; `app/proguard-rules.pro`.
- `app/src/main/AndroidManifest.xml` компоненты, разрешения, FileProvider для установки обновлений.
- `res/` значения, цвета, темы, иконки; `res/xml/file_paths.xml` путь кэша обновлений.
- `fastlane/metadata/` описания и changelog по versionCode (ru-RU, en-US).

### Код приложения `app/src/main/java/tunn/client/`
Запуск и сервисы
- `TunnApp.kt` тонкая оболочка Application (не обновляется), подключает слой кода и вызывает `Boot`.
- `Layer.java` загрузчик слоя: подмешивает dex из `files/layer` перед кодом APK, отдаёт .so, откатывает слой после трёх падений подряд. Тоже не обновляется.
- `Boot.kt` старт приложения (бывшее тело TunnApp), обновляется слоем.
- `MainActivity.kt`, `ComposeActivity.kt`, `BaseUiActivity.kt` окна приложения.
- `TunnService.kt` VpnService в отдельном процессе `:vpn`, супервизор подключения.
- `TunnTileService.kt` плитка быстрых настроек.
- `DpiService.kt`, `Dpi.kt` обход DPI (фрагментация TLS, DoH).
- `TgWs.kt` прокси Telegram через WebSocket.
- `Bridge.kt` связь процессов UI и VPN, общее хранилище.

Логика
- `AppState.kt` главное состояние: серверы, пинги, проверки, настройки.
- `State.kt` лёгкие обёртки состояния.
- `WebBridge.kt` JSON-мост между UI и состоянием, список операций.
- `NativeCore.kt`, `Native.java` вызовы в Rust; `Journal.kt` журнал подключения.
- `Links.kt`, `Importer.kt`, `XrayJson.kt` разбор ссылок и подписок, сборка конфигов Xray.
- `SingBox.kt`, `Awg.kt`, `Wg.kt` конфиги и запуск sing-box, AmneziaWG, WireGuard.
- `Cores.kt`, `SbCores.kt`, `CorePlugins.kt`, `Components.kt` установка и выбор ядер.
- `Ping.kt` замеры пинга (медиана трёх); `Services.kt` проверка Discord/Telegram/YouTube.
- `Tuning.kt` сетевые настройки; `Identity.kt` HWID и маскировка клиента.
- `Net.kt` HTTP с повторами и зеркалами; `Format.kt` форматирование.

Плагины, темы, обновления
- `Plugins.kt` плагины правил и протоколов; `Store.kt` каталог Tunn-plugins.
- `Themes.kt` темы-пакеты; `UiNative.kt` интерфейсы .so.
- `Updates.kt` проверка релизов; `UpdatePack.kt` загрузка, проверка подписи и установка слоя `.Update` / `.fix`.

Интерфейс (Compose)
- `UiScreens.kt` главный экран, кнопка питания, список серверов, настройки.
- `UiPages.kt` страницы настроек, модули, каталоги.
- `UiSheets.kt`, `UiApps.kt` нижние листы и выбор приложений.
- `UiWidgets.kt` общие элементы; `UiLiquid.kt` переключатели и кнопка запуска; `UiGlass.kt` эффект стекла.
- `UiTheme.kt` цвета; `UiState.kt` модель для UI; `AnimConfig.kt` анимации; `AppIcon.kt` смена иконки.

## plugins/ (документация и примеры)
- `README.md` формат плагина правил; `THEMES.md` формат тем; `UI_ABI.md` ABI интерфейсов .so.
- `sdk/fq_plugin.h`, `sdk/fq_ui.h` заголовки; `sdk/example/echo.c` пример протокола; `sdk/ui-example-rs/` пример интерфейса на Rust.
- `samples/` готовые образцы плагинов и тем.

## Формат обновлений Tunn (меняет код без установки APK)
Файлы кладутся в GitHub Release. Оба типа zip, внутри `manifest.json`, `manifest.sig` и файлы.
- `Tunn-<версия>.Update` (например `Tunn-4.3.Update`): весь код приложения. Содержит `dex/classesN.dex` и `lib/<abi>/libtunn_service.so`; полностью перекрывает код APK. Может нести `plugins/` и `themes/`.
- `Tunn-<версия>.<номер>.fix` (например `Tunn-4.3.1.fix`): точечное исправление отдельных классов (`dex/fixN.dex`), ставится поверх `.Update` или APK той же версии 4.3, номера возрастают. Может нести `.so`, `plugins/`, `themes/`.
- `manifest.json`: `format: 2`, `kind`, `version`, `shell` (уровень оболочки Layer.SHELL), `files: [{path, sha256}]`. `manifest.sig` подпись ECDSA P-256/SHA-256, публичный ключ в `assets/tunn_pack.pub`. Без верной подписи пакет отклоняется.
- Применяется при следующем запуске: Layer ставит dex перед dex APK. Если после слоя три запуска подряд не дожили до 20 секунд, слой отключается (`state.bad`) и работает код APK.
- Не обновляются: `TunnApp`, `Layer`, манифест, ресурсы, библиотеки внутри APK (Compose, libv2ray). Для них нужен новый APK.
- Сборка: `python3 tools/pack.py keygen` (один раз), затем `build.sh` сам делает `.Update` из APK; `.fix`: `python3 tools/pack.py fix --old-classes dist/classes-A --new-classes dist/classes-B --version 4.3 --serial 1`. Нужен ANDROID_HOME (d8).
- `tools/test_pack.py` проверяет упаковку, подпись и diff классов.
