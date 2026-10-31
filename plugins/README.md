# Плагины Tunn

Плагин — это `.zip` с файлом `plugin.json` и, по необходимости, `.so`, `ui.js`, `ui.css`. Ставится в приложении: Настройки → Движок и плагины → Плагины (из файла или по ссылке). Файлы лежат в `files/plugins/<id>@<версия>/`, включение и значения настроек хранятся отдельно. Версии одного плагина живут рядом.

Плагины выполняют код. Ставьте только из источников, которым доверяете.

## plugin.json

```json
{
  "id": "amneziawg",
  "family": "amneziawg",
  "name": "AmneziaWG",
  "version": "1.0.20250601",
  "author": "...",
  "description": "...",
  "kinds": ["protocol", "rules", "ui"],
  "protocol": {
    "label": "AmneziaWG",
    "lib": "libfq_awg.so",
    "handlesWireGuard": true,
    "multiline": false,
    "match": { "schemes": ["awg"], "contains": ["[interface]", "jc"], "regex": "" },
    "endpointRegex": ""
  },
  "rules": {
    "bypassApps": [], "onlyApps": [],
    "directDomains": [], "proxyDomains": [], "blockDomains": [], "directIps": [],
    "fromSettings": { "bypassApps": "apps" }
  },
  "ui": { "js": "ui.js", "css": "ui.css" },
  "settings": [
    { "key": "apps", "type": "text", "title": "...", "default": "" },
    { "key": "on", "type": "switch", "title": "..." },
    { "key": "level", "type": "select", "options": ["A", "B"] },
    { "key": "n", "type": "number", "min": 0, "max": 20, "step": 1 }
  ]
}
```

- `protocol.match` решает, какие серверы обрабатывает плагин: по схеме ссылки, по вхождению всех подстрок (без учёта регистра) или по регулярному выражению. `multiline: true` означает, что вставленный многострочный текст (например `.ovpn`) считается одним сервером.
- `handlesWireGuard: true` позволяет выбрать плагин движком для обычных WireGuard-конфигураций (Настройки → Движок WireGuard).
- `rules` складываются из всех включённых плагинов. Домены без префикса получают `domain:`; можно писать `geosite:...`, `full:...`. `bypassApps` добавляются в исключения VPN, `onlyApps` работают, если в настройках приложений выбран режим «Все». `fromSettings` берёт список из значения настройки плагина.
- `ui.js` выполняется в интерфейсе после запуска, `ui.css` подключается как стиль.

## UI-плагин

```js
FasdeqPlugin.define("my-plugin", function (api) {
  api.addHomeCard(api.h("div", { class: "svc-card" }, "Привет"));
  api.addSettingsGroup("Мой плагин", [api.h("div", { class: "row" }, "Строка")]);
  api.onState(function (s) { /* s.status, s.rx, s.tx, s.servers ... */ });
  api.act("open", "https://example.com");
  api.get("key"); api.set("key", value);
});
```

Доступно: `h`, `icon`, `snack`, `act`, `state`, `onState`, `get`, `set`, `addSettingsGroup`, `addHomeCard`, `sheet`, `closeSheet`.

## Нативный протокол (.so)

Сишный интерфейс в `sdk/fq_plugin.h`, ABI версии 1:

| Функция | Назначение |
| --- | --- |
| `int fq_abi_version(void)` | вернуть 1 |
| `const char* fq_info(void)` | JSON с описанием |
| `const char* fq_prepare(const char* config)` | параметры туннеля: `{"address":["10.0.0.2/32"],"routes":["0.0.0.0/0"],"dns":["1.1.1.1"],"mtu":1280}` или `{"error":"..."}` |
| `int fq_start(const char* config, int tun_fd)` | запустить без блокировки, 0 при успехе |
| `void fq_stop(void)` | остановить |
| `int fq_ping(const char* config, int timeout_ms)` | задержка в мс, необязательно |
| `const char* fq_last_error(void)` | текст последней ошибки, необязательно |
| `const char* fq_stats(void)` | `{"rx":0,"tx":0}`, необязательно |

`config` приходит как `{"conf":"<текст сервера>","settings":{...значения настроек плагина...}}`. Приложение само создаёт TUN по ответу `fq_prepare`, передаёт файловый дескриптор в `fq_start` и исключает себя из VPN, поэтому сокетам плагина `protect()` не нужен. Минимальный пример: `sdk/example/echo.c`.

## Примеры

- `samples/ru-bypass.zip` — правила: российские домены напрямую, свои приложения и домены в настройках.
- `samples/session-card.zip` — дополнение интерфейса: карточка трафика на главной.
