# pyrun

`pyrun` is an independent Norm library for dispatching Python scripts or modules as child processes. The source entry point is [`norm/libraries/pyrun`](../../norm/libraries/pyrun).

The library models Python commands and execution requests while reusing [`std.process`](../stdlib/cli) for process lifecycles. Argument arrays never pass through a shell; working directories, environment variables, stdin, timeouts, cancellation, and stdout/stderr limits follow `std.process`.

`pyrun` does not bundle Python. Callers can provide a system interpreter, an interpreter from a project `venv`, or another executable. `pyrun_embedded` is the companion fixed-version runtime policy library; the host runtime layer uses [`PythonRuntimeService`](../../cli/compiler/src/main/java/dev/w0fv1/norm/pyrun/PythonRuntimeService.java) to read the fixed catalog, download and verify archives, cache interpreters, create project `.norm/python` environments, and install `requirements.txt`.

Python virtual environments should be rebuilt from project dependency declarations rather than committed or moved. See the [official `venv` documentation](https://docs.python.org/3/library/venv.html) for Python's rules.

Current acceptance uses the real `pypa/sampleproject`: the host downloads a pinned CPython runtime, creates a venv, installs the project through requirements, and then Norm `pyrun` starts a smoke script. This project is the official PyPA packaging tutorial example; its entry point and dependencies are defined in [`pyproject.toml`](https://github.com/pypa/sampleproject/blob/main/pyproject.toml).
