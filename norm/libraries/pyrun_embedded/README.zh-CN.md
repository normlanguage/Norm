# pyrun_embedded

`pyrun_embedded` 是 Norm 管理的 Python 运行时配套库。运行时版本由 Norm 发行版固定，不会静默切换到最新 Python。

当前库只暴露发行版策略。运行时下载、平台归档校验、缓存和 `venv` 创建属于宿主运行时清单；这些能力完成后，本包才会成为默认的下载型入口。
