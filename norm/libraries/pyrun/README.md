# pyrun

`pyrun` is the independent Norm library for running Python as a child process. It has no Python runtime dependency of its own: callers may pass a Python executable, a virtual-environment executable, or rely on the host's `python` command.

The library reuses `std.process` for process creation, argument boundaries, working directory, environment overrides, stdin, timeout, cancellation, and bounded output.

```norm
import pyrun.PythonEntry
import pyrun.PythonRequest
import pyrun.runPython

var result = runPython(PythonRequest(
  entry: PythonEntry.Script(path: "scripts/task.py"),
  interpreter: ".venv/Scripts/python.exe",
  arguments: ["--name", "Norm"]
))
```

`pyrun` does not invoke a shell. A non-zero Python exit is returned as a `ProcessResult`; request validation and process startup failures retain the existing `std.process` boundary.

The managed runtime and fixed-version bundled runtime are delivered by the companion `pyrun_embedded` library and the Norm distribution runtime catalog. They are deliberately kept out of this package so projects that already own Python do not acquire a runtime policy implicitly.
