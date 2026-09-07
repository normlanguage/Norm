# Micronaut 单文件应用

在本目录运行：

```text
norm web.norm
```

应用启动后访问 `http://127.0.0.1:8080/hello/Norm`。单文件本地应用不需要声明 `package`、Module 名称或版本。应用 Repository 继承 `Repository<E, I>` 获得常用持久化操作，事务 Store 由 `RepositoryContext` 管理。每次请求都会把路径中的名称写入 H2；`save` 返回已持久化的实体，持久化失败直接抛出异常。Service 的 `Result` 只表达业务失败。

`micronaut.web@4` 的 `DataSources()` 默认使用内存数据库，不产生数据库文件，进程退出后数据消失。使用文件数据库可传入 `DataSources(defaultSource: h2DataSource(storage: H2Storage.File))`，并导入 `h2DataSource` 和 `H2Storage`。未指定 `database` 时在对外交付的 EXE 所在目录生成 `application.mv.db`；源码运行时使用入口源码目录。显式传入的数据库路径保持原意。应用路径契约见[应用构建](/tooling/application-build)。

生成本机可执行文件：

```text
norm build web.norm
```

Windows 生成 `web.norm.exe`。它是 Native Image，不需要 Norm 或 Java，启动时也不再解包 JVM。
