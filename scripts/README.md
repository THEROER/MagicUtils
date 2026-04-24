Release helpers for MagicUtils live in this directory.

Current entrypoint:

- `publish_release.py`: validates a new version, optionally bumps
  `gradle.properties`, pushes the selected branch, waits for the remote ref to
  catch up, and dispatches the GitHub Actions release workflow.
