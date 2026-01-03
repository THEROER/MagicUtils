# Versioning

This site is versioned with `mike` and published to GitHub Pages. Each
documentation version matches a MagicUtils release tag.

## Documentation versions

The version selector in the header switches between:

- `stable`: the latest tagged release.
- `dev`: the current development branch.

Examples in the docs use `{{ magicutils_version }}` which is automatically
set to the active docs version.

## Tag releases (for maintainers)

When you tag a release (for example `v1.10.0`), the workflow deploys a new
docs version and updates the `stable` alias.

```
# Local
mike deploy --update-aliases 1.10.0 stable
mike set-default stable
```

## Development docs

For development builds, the workflow deploys `dev` and sets `latest` as the
default alias.

```
# Local
mike deploy --update-aliases dev latest
mike set-default latest
```
