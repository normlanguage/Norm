# pyrun_embedded

`pyrun_embedded` 是 Norm 管理的 Python 运行时配套库。运行时版本由 Norm 发行版固定，不会静默切换到最新 Python。

当前发行版固定使用 CPython `3.14.8+20261013`。宿主运行时层读取同一份清单，选择平台归档并校验 SHA-256，缓存解压后的解释器，在项目 `.norm/python` 中创建虚拟环境，并安装声明的 `requirements.txt`。
