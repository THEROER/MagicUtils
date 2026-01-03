# Contributing

## Build

```bash
./gradlew build
```

## Build the docs locally

```bash
python -m pip install mkdocs-material mkdocs-macros-plugin mike
mkdocs serve
```

## Deploy versioned docs (maintainers)

```bash
mike deploy --update-aliases 1.10.0 stable
mike set-default stable
```

For dev docs:

```bash
mike deploy --update-aliases dev latest
mike set-default latest
```
