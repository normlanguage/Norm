# 64 本地依赖

应用通过 `module.norm` 声明对 `library` 的直接依赖。先把独立的库模块打包到本地包仓库，再运行消费者；应用只需要依赖声明和公开的 `Greeting` 类型。

库模块描述：

<<< ../../../norm/tests/docs/dependency/producer/library/module.norm{norm}

公开类型：

<<< ../../../norm/tests/docs/dependency/producer/library/model/Greeting.norm{norm}

消费者模块描述：

<<< ../../../norm/tests/docs/dependency/app/module.norm{norm}

消费者入口：

<<< ../../../norm/tests/docs/dependency/app/Main.norm{norm}

从仓库根目录准备本地包并运行（`--output` 是本机的 Norm 包缓存目录）：

```sh
norm package norm/tests/docs/dependency/producer/library/module.norm --output "$HOME/.norm/cache/packages"
norm run norm/tests/docs/dependency/app/Main.norm
```

预期输出：

<<< ../../../norm/tests/docs/dependency/expected.out{text}

库项目放在消费者源树之外。自动验收还会将消费者复制到没有库源码的目录，以确认运行时使用打包依赖。

动手试试：修改库中的问候文本，重新打包，再运行消费者观察新结果。

精确规则：[模块与依赖](/zh/spec/module-system)。
