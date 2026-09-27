# Hello, Norm

Run these independent examples in order, or open any source file and change it. Each program is a single `.norm` file. Run the commands from the directory containing the files.

`norm <file>` runs the source on the JVM. `norm build <file>` builds a native executable for your platform; run the generated program without Norm or Java installed on the target machine. Native builds need the [Native Image and platform C toolchains](https://normlanguage.github.io/Norm/tooling/application-build).

| File | JVM run | Native build | Windows output and run | Linux/macOS output and run | Explore |
| --- | --- | --- | --- | --- | --- |
| `hell.norm` | `norm hell.norm` | `norm build hell.norm` | `hell.norm.exe` → `.\hell.norm.exe` | `hell` → `./hell` | The entry point, a variable, and output. |
| `sort.norm` | `norm sort.norm` | `norm build sort.norm` | `sort.norm.exe` → `.\sort.norm.exe` | `sort` → `./sort` | Bubble sort, loops, and collection value semantics. |
| `maze.norm` | `norm maze.norm` | `norm build maze.norm` | `maze.norm.exe` → `.\maze.norm.exe` | `maze` → `./maze` | Breadth-first search and a printed shortest route. |
| `todo.norm` | `norm todo.norm` | `norm build todo.norm` | `todo.norm.exe` → `.\todo.norm.exe` | `todo` → `./todo` | A desktop Todo app with local persistence. |
| `board.norm` | `norm board.norm` | `norm build board.norm` | `board.norm.exe` → `.\board.norm.exe` | `board` → `./board` | A local web message board. |

The first three programs use the bundled standard library. The desktop and web examples require the library versions declared in their source files. The Todo app needs a graphical session. Type a task and choose **Add** or press Enter; it saves `todo.json` beside `todo.norm`.

The board serves [http://127.0.0.1:8080/](http://127.0.0.1:8080/) and stores its database as `application.mv.db` beside `board.norm`. Stop it with Ctrl+C.

Read the [Chinese guide](README.zh-CN.md), the [Norm learning path](https://normlanguage.github.io/Norm/learn/), or the [language reference](https://normlanguage.github.io/Norm/spec/language-spec).
