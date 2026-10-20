# pyrun

`pyrun` 是独立的 Norm 库，用于把 Python 程序作为子进程运行。它不携带 Python 运行时；调用方可以显式传入 Python 可执行文件、虚拟环境中的解释器，或使用宿主环境中的 `python` 命令。

库复用 `std.process`，因此参数边界、工作目录、环境覆盖、标准输入、超时、取消和有界输出保持一致。

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

`pyrun` 不经过 Shell。Python 非零退出通过 `ProcessResult` 返回；请求校验和进程启动失败沿用 `std.process` 的边界。

托管运行时和固定版本的内置运行时由配套的 `pyrun_embedded` 库及 Norm 发行版运行时清单提供，不放入本包，避免已有 Python 的项目隐式获得运行时策略。
