# 发行版依赖预检

[`distribution-preflight.mjs`](./distribution-preflight.mjs) 从现有 `toolchain-artifacts.json` 读取运行时依赖图，采集 Debian 或 Fedora 当前系统根中已安装的依赖证据。它不声明依赖版本，不编译产品，不安装软件，不访问远程仓库。Node 内置模块是其全部执行依赖。

## 输入与范围

输入是构建生成的 schema 1 工具链清单。生成器仍是依赖图的唯一来源；工具验证清单中的根、边、物理组件归属、用途和摘要字段，保留合并 JAR 对应的逻辑组件。不得用测试夹具或手写坐标清单替代正式调查输入。

输入文件的 SHA-256 记录在报告中，但不证明清单对应当前源码。调查记录还须绑定源码提交、实际构建参数、输入供应方式和生成日志；`catalogSourceBinding` 在本工具中保持 `not-verified`。

本工具只覆盖清单中提供的运行时图。不宣称覆盖 annotation processor、测试、插件及插件依赖、全部父 POM/BOM、JDK、Native Image 工具或额外 metadata 归档。缺失的覆盖域在报告中明确标记 `not-assessed`，不得将该报告作为完整 `Build-Depends` 或 `BuildRequires` 清单直接使用。

## 采集

在待调查的 Debian 系统根中运行：

```sh
mkdir -p evidence
node cli/compiler/scripts/distribution-preflight.mjs collect \
  --target debian \
  --catalog /path/to/generated/toolchain-artifacts.json \
  --output evidence/debian-runtime.json
node cli/compiler/scripts/distribution-preflight.mjs render \
  --input evidence/debian-runtime.json \
  --output evidence/debian-runtime.md
```

Fedora 使用相同入口，将 `--target` 改为 `fedora` 并使用不同输出文件。执行前须已提供 Node 和目标系统的包查询工具。工具不自动安装它们，也不为调查启动任何容器。

报告记录实际 `/etc/os-release`、架构、包清单、包查询结果及输入摘要。`--target` 只选择发行版类型，不代表 Debian sid 或 Fedora Rawhide；目标 suite 和仓库快照必须另行验证。不能在 Ubuntu 等衍生系统中采集后标记为 Debian。

Debian 分支检查 `/usr/share/maven-repo` 中的候选 POM/JAR，包括版本别名。它记录文件 SHA-256、真实路径、文件和符号链接的包所有权、二进制包版本及源包身份。目录版本或包版本不同于上游版本时，不据此自动判断 API 不兼容；同版本也不证明字节或行为等价。

Fedora 分支查询本机 RPM 数据库中的 Maven capability，不按 artifact 名猜 RPM 包名。它同时枚举已安装包提供的版本化 capability，区分普通 JAR、POM 和 classifier。仅有非默认坐标时报告 `mapping-required`；默认包存在时仍保留兼容包候选。不按版本相近自动映射或判定 API 兼容。它记录提供者、artifact 版本与源 RPM；当前证据层级是 RPM capability，尚未核验对应 JAR/POM 文件、字节、模块描述符或完整 artifact 映射。

## 结果语义

| 结果 | 含义 |
| --- | --- |
| `installed-candidate` | 当前根中存在该分支所定义的系统依赖候选证据；兼容性仍未测试 |
| `mapping-required` | 存在版本化 Maven capability，但尚未配置坐标映射或验证兼容性 |
| `not-installed` | 当前根中未找到候选，不表示发行版仓库缺包 |
| `incomplete-candidate` | 找到部分文件，但不足以形成所需的 POM/JAR 候选 |
| `unverified-candidate` | 文件存在，但文件或 Maven 路径的系统包归属无法确认 |
| `probe-error` | 查询、读取、环境或证据格式失败，不能解释成缺包 |

`collect` 返回 0 表示全部项目存在已安装候选证据；返回 2 表示存在待映射、未安装、不完整或归属未确认的项目；返回 1 表示输入或采集错误。结果文件中保留逐项错误，前置条件失败时可能不会生成报告。`render` 成功时返回 0。

任何退出码都不代表源码构建通过。报告的 `readiness` 保持 `not-established`，JPMS、API 兼容性、外部网络隔离、源码重建、安装和系统库升级验收保持未完成。文件摘要仅记录采集时观察到的字节，不等同于发行版签名验证或软件包完整性认证。

输出使用独占创建，不覆盖已有证据。采集不写系统仓库或用户缓存。包清单在采集前后发生变化时，拒绝输出成功报告。报告中的时间来自执行环境时钟，归档时须记录时钟偏差。

## 测试

```sh
node --test cli/compiler/scripts/distribution-preflight.test.mjs
```

定向测试使用临时目录和系统命令夹具，覆盖错误分类、组件归属、符号链接、版本别名、报告范围和真实 Node 子进程。它们不替代 Debian sid、Fedora Rawhide 中使用真实 Norm 清单的采集，也不替代 `sbuild`、`mock` 或安装后验收。
