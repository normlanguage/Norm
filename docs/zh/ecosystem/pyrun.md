# pyrun

`pyrun` 是独立的 Norm 库，用于把 Python 脚本或模块作为子进程交给 Norm 调度。源码入口见 [`norm/libraries/pyrun`](../../../norm/libraries/pyrun)。

库只负责 Python 命令建模和执行请求，进程生命周期复用 [`std.process`](../stdlib/cli)。参数数组不会经过 Shell；工作目录、环境、stdin、超时、取消和 stdout/stderr 上限沿用 `std.process`。

`pyrun` 不携带 Python。调用方可以传入系统解释器、项目 `venv` 中的解释器或其他可执行文件。`pyrun_embedded` 是配套的固定版本运行时策略库；宿主运行时层通过 [`PythonRuntimeService`](../../../cli/compiler/src/main/java/dev/w0fv1/norm/pyrun/PythonRuntimeService.java) 读取固定清单、下载并校验归档、缓存解释器、创建项目 `.norm/python` 环境和安装 `requirements.txt`。

Python 虚拟环境应由项目依赖声明重建，不应提交或移动 `.venv` 目录。Python 官方的 `venv` 规则见[官方文档](https://docs.python.org/3/library/venv.html)。

当前验收包含真实的 `pypa/sampleproject`：宿主层下载固定 CPython、创建 venv、通过 requirements 安装项目，再由 Norm `pyrun` 启动 smoke script。该项目是 PyPA 官方打包教程的开源示例，入口和依赖定义见其 [`pyproject.toml`](https://github.com/pypa/sampleproject/blob/main/pyproject.toml)。
