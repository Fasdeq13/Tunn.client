# Нативные интерфейсы (.so на Rust, C или C++)

Плагин может заменить весь интерфейс Tunn. Библиотека рисует кадры в буфер RGBA, Tunn показывает их на экране и передаёт касания. Заголовок: `sdk/fq_ui.h`, готовый пример на Rust: `sdk/ui-example-rs/`.

## plugin.json

```json
{
  "id": "my-ui",
  "name": "Мой интерфейс",
  "version": "1.0.0",
  "author": "...",
  "description": "...",
  "kinds": ["ui-native"],
  "uiNative": { "lib": "libmy_ui.so" }
}
```

Архив: `plugin.json` и `.so` в корне. Библиотека собирается под `arm64-v8a` (и по желанию `armeabi-v7a`, `x86_64`).

## Функции (ABI 1)

| Функция | Когда вызывается |
| --- | --- |
| `int fq_ui_abi(void)` | вернуть `1` |
| `const char* fq_ui_info(void)` | JSON `{"name","version"}` |
| `int fq_ui_init(int w, int h, float density)` | при запуске, 0 при успехе |
| `void fq_ui_resize(int w, int h)` | необязательно, при смене размера |
| `void fq_ui_state(const char* json)` | состояние приложения изменилось |
| `void fq_ui_touch(int kind, float x, float y)` | 0 нажатие, 1 движение, 2 отпускание, 3 отмена; координаты в пикселях кадра |
| `void fq_ui_back(void)` | необязательно, системная кнопка «назад» |
| `int fq_ui_render(uint8_t* rgba, int w, int h, int stride, uint32_t ms)` | примерно 60 раз в секунду: 1 кадр изменился, 0 без изменений (буфер не копируется), меньше 0 ошибка |
| `const char* fq_ui_poll(void)` | вернуть следующее действие JSON или `NULL` |
| `void fq_ui_close(void)` | необязательно, при выходе |

Кадр рисуется в уменьшенном разрешении (около 62% от экрана), `density` уже учитывает это. Пиксели: `R,G,B,A`, альфа 255. Строки, возвращаемые плагином, должны жить до следующего вызова.

## Состояние (`fq_ui_state`)

```json
{
  "status": "Off | Connecting | On | Stopping",
  "server": {"id": "...", "name": "..."},
  "servers": [{"id": "...", "name": "...", "proto": "vless", "group": "", "ping": 42}],
  "rx": 0, "tx": 0, "down": 0, "up": 0, "uptime": 0,
  "dark": 0
}
```

`ping` равен -1, если сервер не измерен или не отвечает.

## Действия (`fq_ui_poll`)

`{"op":"toggle"}`, `{"op":"connect"}`, `{"op":"disconnect"}`, `{"op":"select","id":"..."}`, `{"op":"exit"}` (вернуть стандартный интерфейс).

## Безопасность

Библиотека выполняется внутри приложения со всеми его правами. Ставьте только из источников, которым доверяете. Если интерфейс не загрузился, вернул ошибку или приложение закрылось в первые секунды после запуска, при следующем старте он отключается автоматически. Кнопка × в углу экрана всегда возвращает стандартный интерфейс.

## Сборка на Rust

    cargo install cargo-ndk
    rustup target add aarch64-linux-android
    cargo ndk -t arm64-v8a build --release

Крейт с `crate-type = ["cdylib"]`, функции с `#[no_mangle] pub extern "C"`.
