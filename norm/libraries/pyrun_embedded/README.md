# pyrun_embedded

`pyrun_embedded` is the companion library for Norm-managed Python runtimes. Its runtime version is fixed by the Norm distribution and is not silently advanced to the latest Python release.

The library currently exposes the distribution policy only. Runtime acquisition, platform archive verification, cache population, and `venv` creation belong to the host runtime catalog; they must be implemented before this package becomes the default downloader-backed entry point.
