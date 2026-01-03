# Contributing

## Build the docs locally

```bash
python -m pip install mkdocs-material mike
mkdocs serve
```

## Deploy versioned docs

```bash
mike deploy --update-aliases 1.10.0 stable
mike set-default stable
```

For dev docs:

```bash
mike deploy --update-aliases dev latest
mike set-default latest
```
