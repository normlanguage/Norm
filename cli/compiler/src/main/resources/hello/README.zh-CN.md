# Hello, Norm

你可以按顺序运行这五个独立示例，也可以打开任意源码修改。每个程序都只有一个 `.norm` 文件。在这些文件所在目录执行下列命令。

`norm <文件>` 在 JVM 上直接运行源码；`norm build <文件>` 为当前平台构建原生可执行程序。生成的程序运行时不要求目标机器安装 Norm 或 Java。原生构建所需的 Native Image 与各平台 C 工具链见[应用构建文档](https://normlanguage.github.io/Norm/zh/tooling/application-build)。

| 文件 | JVM 运行 | 原生构建 | Windows 产物与运行 | Linux/macOS 产物与运行 | 重点 |
| --- | --- | --- | --- | --- | --- |
| `hell.norm` | `norm hell.norm` | `norm build hell.norm` | `hell.norm.exe` → `.\hell.norm.exe` | `hell` → `./hell` | 程序入口、变量与输出。 |
| `sort.norm` | `norm sort.norm` | `norm build sort.norm` | `sort.norm.exe` → `.\sort.norm.exe` | `sort` → `./sort` | 冒泡排序、循环与集合的值语义。 |
| `maze.norm` | `norm maze.norm` | `norm build maze.norm` | `maze.norm.exe` → `.\maze.norm.exe` | `maze` → `./maze` | 广度优先搜索与终端展示的最短路径。 |
| `todo.norm` | `norm todo.norm` | `norm build todo.norm` | `todo.norm.exe` → `.\todo.norm.exe` | `todo` → `./todo` | 带本地持久化的桌面 Todo。 |
| `board.norm` | `norm board.norm` | `norm build board.norm` | `board.norm.exe` → `.\board.norm.exe` | `board` → `./board` | 本地 Web 留言板。 |

前三个程序只使用随 CLI 提供的标准库。桌面与 Web 示例需要源码所声明版本的库。Todo 需要图形界面。输入任务后点击“Add”或按回车；应用将 `todo.json` 保存在 `todo.norm` 旁边。

留言板地址是 [http://127.0.0.1:8080/](http://127.0.0.1:8080/)，数据库文件 `application.mv.db` 保存在 `board.norm` 旁边。按 Ctrl+C 停止留言板。

继续阅读[英文说明](README.md)、[Norm 教学路径](https://normlanguage.github.io/Norm/zh/learn/)和[语言参考](https://normlanguage.github.io/Norm/zh/spec/language-spec)。
