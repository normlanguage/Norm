# pyrun

`pyrun` 是独立的 Norm 库，用于把 Python 脚本或模块作为子进程交给 Norm 调度。源码入口见 [`norm/libraries/pyrun`](../../../norm/libraries/pyrun)。

库只负责 Python 命令建模和执行请求，进程生命周期复用 [`std.process`](../stdlib/cli)。参数数组不会经过 Shell；工作目录、环境、stdin、超时、取消和 stdout/stderr 上限沿用 `std.process`。

`pyrun` 不携带 Python。调用方可以传入系统解释器、项目 `venv` 中的解释器或其他可执行文件。`pyrun_embedded` 是配套的固定版本运行时策略库，运行时资产、平台清单、下载校验、缓存和 `venv` 创建属于宿主发行版运行时层；它们不在普通 `pyrun` API 中重复实现。

Python 虚拟环境应由项目依赖声明重建，不应提交或移动 `.venv` 目录。Python 官方的 `venv` 规则见[官方文档](https://docs.python.org/3/library/venv.html)。

当前源码级验收通过真实 Python 子进程验证脚本入口、模块入口、参数边界和工作目录；托管运行时的固定版本清单和自动获取能力以发行版运行时实现为准。
