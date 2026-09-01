# Apache Commons Lang

这个示例由两个普通 Module 组成：`app` 只声明并调用 `commons.lang` 依赖；`commons.lang` 使用 Apache Commons Lang 3.20.0 作为内部实现，不包含 Java 源码。

从仓库根目录运行：

```text
./gradlew :compiler:run --args="run docs/examples/java-commons-lang/app/Main.norm"
```

输出：

```text
mroN
```

构造并调用 JVM 对象：

```text
./gradlew :compiler:run --args="run docs/examples/java-commons-lang/object/app/Main.norm"
```

输出：

```text
2
Norm
NAR
first
second
```

更新 Maven 制品版本时，先移除旧的 `resolution` 参数、修改版本，再解析并写入新摘要：

```text
norm resolve docs/examples/java-commons-lang/dependencies/commons/lang
```

适配 Module 可以导出为 Maven 仓库布局中的包：

```text
norm package docs/examples/java-commons-lang/dependencies/commons/lang --output build/norm-repository
```

生成 `commons:lang:1`：

```text
build/norm-repository/commons/lang/1/
├── lang-1.nar
└── lang-1.pom
```

POM 由 `module.norm` 派生，只存在于 `norm package` 指定的发布仓库，不进入适配源码树。

完整设计与第一版类型映射见 [Java Library Adapter](../../design/java-library-adapters.md)。
