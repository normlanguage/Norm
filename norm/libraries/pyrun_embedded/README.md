# pyrun_embedded

`pyrun_embedded` is the companion library for Norm-managed Python runtimes. Its runtime version is fixed by the Norm distribution and is not silently advanced to the latest Python release.

The current distribution pins CPython `3.14.8+20261013`. The host runtime layer reads one manifest, selects a supported platform archive, verifies SHA-256, caches the extracted interpreter, creates a project `.norm/python` virtual environment, and installs the declared `requirements.txt`.
