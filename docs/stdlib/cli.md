# 命令行与进程

`std.cli` 用 Norm 实现参数解析；`std.application`、标准流和 `std.process` 复用系统运行时，不新增第三方依赖。

## 参数与运行环境

```norm
import std.application.arguments
import std.cli.CommandLine
import std.cli.Option
import std.cli.OptionKind

Void main() {
  CommandLine command = CommandLine(name: "gait", description: "Git assistant", options: [
    Option(name: "json", description: "JSON output", kind: OptionKind.Flag),
    Option(name: "repo", description: "Repository directory", kind: OptionKind.Value)
  ])
  var parsed = command.parse(arguments: arguments(), stopAtPositional: true)
  printLine(parsed.value(name: "repo") ?? ".")
}
```

源码运行时使用 `norm run application.norm -- --repo "my repo" 创建分支`。生成的应用直接接收参数。解析器消费已有参数数组，保留空格和空字符串；定义、帮助文本和解析规则共用同一组选项。

公开声明与失败类型见 [`std.cli`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/cli/arguments.norm)。环境变量、工作目录、参数和完成码见 [`std.application`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/application/application.norm)。`setExitCode` 设置正常返回后的完成码，不中断资源清理。

## 标准流与子进程

[`std.io.console`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/io/console.norm) 提供借用的标准输入和文本输出，应用不能关闭宿主标准流。字节读取和 UTF-8 转换复用 [I/O 协议](/stdlib/io)。

Native 应用在 Windows 控制台通过 Unicode 接口读写，输入转换为 UTF-8 字节，不修改终端代码页；stdout 和 stderr 分别识别控制台，重定向到文件或管道时输出 UTF-8。宿主实现见 [NativeStandardStreams](../../cli/compiler/src/main/java/dev/w0fv1/norm/runtime/NativeStandardStreams.java)。

[`std.process`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/process/process.norm) 通过可执行文件和参数数组启动子进程，不经过 Shell。非零退出、超时和取消是结果；非法请求、启动失败和 I/O 失败是可捕获异常。输出预算分别作用于 stdout 和 stderr；超出部分继续排空，并在结果中标记截断。

进程结束不代表外部效果回滚。取消和超时会终止受管理进程及已观察到的后代；这不是操作系统级进程隔离。调用方仍需验证 Git 等外部操作的实际结果。

验收入口：[参数与标准流](https://github.com/normlanguage/Norm/tree/main/cli/compiler/src/test/java/dev/w0fv1/norm/stdlib)、[真实子进程](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/platform/jdk/JdkProcessRunnerTest.java)。
