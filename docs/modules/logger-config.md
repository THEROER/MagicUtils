# Logger config reference

`logger.{ext}` stores logger settings. On Bukkit it lives in the plugin config
folder. On Fabric it is namespaced under `config/<modid>/` by default.

Formats:

- JSON/JSONC by default
- YAML/TOML when the format helpers are available

## Minimal example

```yaml
plugin-name: DonateMenu
short-name: DM
auto-localization: true
debug-placeholders: false

prefix:
  chat-mode: FULL
  console-mode: SHORT
  custom: "[DM]"
  use-gradient-chat: true
  use-gradient-console: false

defaults:
  target: BOTH
  text-max-length: 262144
  placeholder-engine-order: [MINI_PLACEHOLDERS, PB4, PAPI]
  miniplaceholders-mode: COMPONENT
  pb4-mode: COMPONENT

chat:
  auto-generate-colors: true
  gradient: ["#7c3aed", "#ec4899"]
  colors:
    error: ["#ff4444", "#cc0000"]
    warn: ["#ffbb33", "#ff8800"]
    debug: ["#33b5e5", "#0099cc"]
    success: ["#00c851", "#007e33"]

console:
  auto-generate-colors: true
  gradient: ["#ffcc00", "#ff6600"]
  strip-formatting: false
  colors:
    error: ["#ff4444", "#cc0000"]
    warn: ["#ffbb33", "#ff8800"]
    debug: ["#33b5e5", "#0099cc"]
    success: ["#00c851", "#007e33"]

help:
  use-logger-colors: true
  primary-color: "#ff55ff"
  muted-color: "gray"
  text-color: "white"
  line: "-----------------------------"
  page-size: 7
  max-enum-values: 8

sub-loggers:
  Commands:
    enabled: true
```

## Key sections

### plugin-name / short-name

Auto-filled on first run and used in prefix rendering. `short-name` is used by
`PrefixMode.SHORT`.

### auto-localization

When enabled, any message that starts with `@` is treated as a language key and
resolved via `LanguageManager`.

### prefix

- `chat-mode` / `console-mode`: `NONE`, `SHORT`, `FULL`, `CUSTOM`
- `custom`: custom prefix string for `CUSTOM`
- `use-gradient-chat` / `use-gradient-console`: apply gradient to prefix

### defaults

- `target`: `CHAT`, `CONSOLE`, `BOTH`
- `text-max-length`: max JSON length (Fabric only)
- `placeholder-engine-order`: preferred external placeholder order
- `miniplaceholders-mode`: `COMPONENT` or `TAG`
- `pb4-mode`: `COMPONENT` or `RAW`

### chat / console

Color palettes and gradients. Set `auto-generate-colors` to `false` to force
custom palettes.

### help

Controls the built-in help renderer formatting and pagination.

### sub-loggers

Per-prefix enable flags for `logger.withPrefix(...)` instances.
