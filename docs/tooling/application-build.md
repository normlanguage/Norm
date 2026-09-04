---
title: 应用构建
description: 将 Norm 应用构建为自包含可执行文件
---

# 应用构建

Windows x64 的正式 `norm.exe` 可以把应用、确定版本的 NAR、Java 制品和 Norm 运行时合并为一个无需安装的可执行文件。构建时完成依赖解析与类型检查；生成的程序运行时不访问 GitHub、Maven 或 Gradle，也不要求目标机器安装 Norm 或 Java。

## 单文件

在源文件所在目录执行：

```text
norm build web.norm
```

产物是同目录的 `web.norm.exe`。文件名保留 `.norm`，使源码与产物保持直接对应。

## 项目

项目以根 package 中的 `module.norm` 和 `application.norm` 为入口。在项目目录执行：

```text
norm build
```

也可以显式选择项目目录：

```text
norm build .
```

产物写入 `<module>/build/<artifact>.exe`；`artifact` 使用 Module 仓库坐标映射，例如 `hello.web` 生成 `build/web.exe`。

## 运行模型

应用首次启动时按内容摘要解出运行内容，后续启动复用 `%LOCALAPPDATA%\Programs\Norm\applications\<sha256>`。不同应用内容不会共享可变目录；删除该缓存只会让下次启动重新解出，不影响 EXE。

当前应用构建目标是 Windows x64。平台发行物和校验规则见[发布流程](/design/release-process)。
