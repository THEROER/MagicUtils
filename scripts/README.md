Release helpers for MagicUtils live in this directory.

Current entrypoint:

- `publish_release.py`: validates a new version, optionally bumps
  `gradle.properties`, pushes the selected branch, dispatches the GitHub
  Actions release workflow, watches the chain to completion, and smoke-tests
  the published artifact.

For the full release process — flags, pipeline diagram, troubleshooting,
and verification commands — see [`../RELEASING.md`](../RELEASING.md).
