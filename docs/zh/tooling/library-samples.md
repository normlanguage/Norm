---
title: 库示例
description: 使用匹配的软件包运行各库仓库中的 Norm 示例
---

# 库示例

每个库的 `samples/` 索引指向本库示例或实际所属库的示例。先阅读中英文索引，再按所属示例的运行或测试命令操作。示例中的 `Module module()` 是软件包名称与版本的唯一声明源。[库仓库列表](https://github.com/orgs/normlanguage/repositories)提供索引；[Norm `hello` 示例](/zh/tooling/)展示无需单独检出库仓库的完整程序。

Norm CLI 与库 NAR 必须使用匹配的编译器 ABI。相应版本公开后，所属示例的命令会正常解析声明的依赖。仅检出源码并不会安装软件包；依赖图中的每个版本都要有对应的包。各库 GitHub Release 附有正式产物及其 SHA-256 校验文件。

## 准备尚未发布的源码

新版本发布前，在独立的 Norm 用户目录中准备候选包。按依赖顺序，针对各库的 `module.norm` 执行标准 `norm package` 命令。若改动后的包所用版本已有公开产物，应声明新版本；不得替换已有资产，也不得混用依赖版本不一致的包。消费示例的依赖版本需要同步更新。

Norm CLI 会启动 Java，经由 `norm.exe` 启动时也是如此。将该 Java 进程的 `user.home` 指向独立开发目录，再输出到该目录中的 `.norm/cache/packages`。例如，在包含 `commons-lang` 的工作区中使用 Unix shell：

```sh
export JAVA_TOOL_OPTIONS="-Duser.home=$PWD/.norm-sample-home"
norm package commons-lang/commons/lang/module.norm --output "$PWD/.norm-sample-home/.norm/cache/packages"
```

在 PowerShell 中设置 `$env:JAVA_TOOL_OPTIONS = "-Duser.home=$PWD/.norm-sample-home"`，输出目录相同。随后运行示例时保持该设置，让 CLI 从同一个目录解析软件包。

软件包命令检查模块，生成 NAR 与校验文件。若模块还依赖其他库，应先准备其声明的版本。[模块源码](https://github.com/orgs/normlanguage/repositories)与[发布流程](/zh/design/release-process)给出各版本的源码和正式产物。候选包仅供开发验证；只有整个依赖图发布并更新示例的声明版本后，示例才可供普通用户直接安装运行。
