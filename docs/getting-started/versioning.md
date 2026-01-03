# Documentation versioning

This site is versioned with `mike` and published to GitHub Pages.

## Tag releases

When you tag a release (for example `v1.10.0`), the workflow deploys a new
version and updates the `stable` alias.

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
