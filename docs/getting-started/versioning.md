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

Use the release helper to sync `gradle.properties`, push the release branch,
and dispatch the tag workflow safely:

```bash
python3 scripts/publish_release.py 1.10.0 --dry-run
python3 scripts/publish_release.py 1.10.0
```

The helper waits until `origin/<ref>` resolves to the pushed local HEAD before
dispatching `release.yml`, which avoids tagging an older commit when GitHub has
not caught up with the branch update yet.

When a release tag is created (for example `v1.10.0`), the workflow deploys a
new docs version and updates the `stable` alias.

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
