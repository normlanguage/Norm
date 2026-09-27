# 参与 Norm 开发

[English](CONTRIBUTING.md)

Norm 正处于编译器引导阶段。修改应保持语言规范与 Java 实现同步，但不得因规范中的功能尚未完成，就意外将其加入编译器。

## 环境要求

- JDK 25；使用仓库附带的 Gradle Wrapper。
- Git；源码文件应能使用 LF 换行符。

构建依赖、本地编译器发行目录和 Java 格式化由根 Gradle Kotlin DSL 构建定义。

## 构建与测试

Unix 类系统：

```shell
./gradlew qualityCheck
./gradlew :compiler:installRuntimeDist
./build/compiler/norm-runtime/bin/norm --version
```

Windows：

```powershell
.\gradlew.bat qualityCheck
.\gradlew.bat :compiler:installRuntimeDist
.\build\compiler\norm-runtime\bin\norm.bat --version
```

提交 Java 修改前运行 `./gradlew spotlessCheck`，或使用 `./gradlew spotlessApply` 格式化；Windows 使用 `.\gradlew.bat`。`qualityCheck` 包含格式检查和测试。CI 在 Java 25 上运行测试，并使用 GraalVM 单独验证 Native Image 行为。

## 架构规则

[工具链开发规范](https://normlanguage.github.io/Norm/zh/design/toolchain-development)是模块边界、包职责、依赖方向、命名和验证的唯一依据。语言修改必须同步规范、前端诊断、Truffle 降级和定向测试。
