# MagicUtils — guide for translators

Welcome! This document explains how to add or update translations in
**any plugin or mod that uses MagicUtils**, including MagicUtils itself.

## TL;DR

1. Find the plugin's English translation file. Three places to look:
   - `lang/en.json` (MagicUtils core messages — MagicUtils repository at
     `lang/src/main/resources/lang/en.json`).
   - `lang/<plugin>/en.json` (plugin-specific bundled translations,
     e.g. `lang/leavepulse/en.json`, `lang/axiomweave/en.json`).
   - `<server>/plugins/<Plugin>/lang/en.json` (live config on a running
     server — overrides bundled values).
2. Copy the file. Rename to your language code: `de.json`, `fr.json`,
   `pl.json`, etc. (see [language codes](#language-codes) below).
3. Translate every value. Leave keys, comments, and `{placeholders}`
   untouched.
4. Drop the file in the same directory and reload the server.

## File format

Translation files are JSONC — **JSON with comments** and trailing
commas. Plain JSON works too.

```jsonc
{
  // Keys use dot-delimited paths; values are MiniMessage strings.
  "language.name": "Українська",
  "language.code": "uk",

  // Placeholders use {curly_braces} — keep them intact when translating.
  "magicutils.commands.no_permission":
      "&cУ вас немає прав для виконання цієї команди!",

  // MiniMessage tags are allowed.
  "axiomweave.command.reload.success":
      "<gray>[<aqua>AxiomWeave</aqua>]</gray> <green>Перезавантажено.</green>"
}
```

### Three things you must keep intact

1. **Keys** — the part before the colon (`"magicutils.commands.no_permission"`)
   is wired into source code. Never translate or rename a key.
2. **Placeholders** — `{language}`, `{count}`, `{error}`. Keep them
   exactly as written. Word order around them can change to fit your
   language naturally.
3. **MiniMessage tags** — `<red>`, `<click:open_url:...>`, etc. Don't
   translate or remove them. Only translate the visible text between
   the tags.

### Comment keys

Some files use leading-underscore keys like `"_comment_metadata"` to
embed translator notes. They're stripped at runtime — but keeping them
when you copy the file helps the next translator.

## Where translation files live

There are two layers, and you'll usually edit the second one:

### 1. Bundled (inside the plugin jar)

Located under `src/main/resources/lang/` in the source repository.
These are the defaults shipped with the plugin and are baked into the
released jar.

To add a brand-new language to a plugin:

- Open the plugin's source repo.
- Copy `lang/en.json` (or `lang/<plugin>/en.json`) to `lang/<code>.json`.
- Translate the values.
- Submit a pull request.

### 2. On-disk override (live on the server)

When a server runs, MagicUtils writes a copy of the bundled file to
`plugins/<Plugin>/lang/<code>.<extension>`. Server admins edit that
copy to override individual messages without touching the jar.

To customize an existing language on your own server:

- Stop or run a config reload on the plugin.
- Edit the file at `plugins/<Plugin>/lang/<code>.json` (or `.yml` /
  `.toml` depending on plugin defaults).
- Save and run the plugin's reload command.

Edits on disk **never overwrite** the bundled defaults — they layer on
top of them.

## Language codes

Use a short locale tag matching what the Minecraft client reports:

| Code   | Language            |
|--------|---------------------|
| `en`   | English             |
| `uk`   | Ukrainian           |
| `ru`   | Russian             |
| `de`   | German              |
| `fr`   | French              |
| `pl`   | Polish              |
| `es`   | Spanish             |
| `pt-br`| Portuguese (Brazil) |
| `zh-cn`| Chinese (Simplified)|

Check your client's selected language under **Options → Language** —
the code shown in parentheses is the one to use.

## Switching the active language

Server admins can change the active language with the built-in
settings command. From console or in-game:

```
/magicutils settings language uk
```

Per-player languages also work automatically: when a player joins, the
client reports its locale and MagicUtils picks the matching translation
for that player only.

## Fallback chain

If a key is missing from your translation, MagicUtils falls back, in
order:

1. The configured fallback language (`en` by default).
2. The bundled English file inside the jar (always present).
3. The raw key, as a last-resort signal that something is missing.

This means you can ship a partial translation — only translate the
keys you care about, and the rest will fall back to English.

## Verifying your translation

After dropping the file in place:

1. Run the plugin's reload command (commonly `/<plugin> reload`).
2. Trigger a few messages in-game (errors, status, help) and confirm
   the language renders correctly.
3. Check console logs for warnings like `Missing translation for key
   '...'` — this means MagicUtils used English fallback for that key.

For automated checking, run the plugin's tests with your file on the
classpath. Reflection and test scaffolding will fail if your JSON has
syntax errors.

## Common mistakes

- **Translated a placeholder.** `{name}` must stay `{name}`, even in
  Ukrainian or Chinese.
- **Removed a MiniMessage tag.** `<red>...</red>` must stay matched.
  Removing the closing tag can leak red colour into surrounding text.
- **Replaced curly quotes for double quotes.** JSON requires straight
  ASCII quotes (`"..."`). Smart quotes (`„..."`) break parsing.
- **Edited the bundled file in the jar.** Always edit the on-disk
  copy under `plugins/<Plugin>/lang/...`. Editing inside the jar gets
  wiped on every plugin update.

## Help

- Open an issue on the plugin's repository tagged with `i18n` or
  `translation`.
- For MagicUtils itself: <https://github.com/THEROER/MagicUtils>.
