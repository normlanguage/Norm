# 01 Hello, Norm

一个 Norm 程序由 `.norm` 源文件和顶层声明组成。独立脚本只需提供 `main()`。

<<< ../../norm/tests/docs/tour/01_hello.norm{norm}

输出：

```text
Hello, Norm
```

`main()` 是程序入口。它省略了返回类型，因此是 `Void` 顶层函数。`String language` 使用类型前置声明，`printLine` 是默认可用的核心输出函数。

## 源码形状

- 代码块始终使用大括号；
- 行尾分号可以省略；
- 官方 formatter 使用两个空格缩进并省略默认的 `public`；
- 字符串使用双引号，转义使用反斜杠，`${expression}` 执行类型化插值；
- 当前 Lexer 不支持源码注释，具体边界见 [Status](/status)。

## 运行

从 Release 获取 CLI 后运行：

```shell
norm run hello.norm
```

编辑器安装和运行入口见 [Tooling](/tooling/)。完整词法规则见[词法结构](/spec/grammar/lexical)。

下一章：[值与绑定](/learn/bindings)。
