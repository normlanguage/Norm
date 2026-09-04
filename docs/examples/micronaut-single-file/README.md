# Micronaut 单文件应用

在本目录运行：

```text
norm web.norm
```

应用启动后访问 `http://127.0.0.1:8080/hello/Norm`。单文件本地应用不需要声明 `package`、Module 名称或版本。应用 Repository 继承 `Repository<E, I>` 获得常用持久化操作，事务 Store 由 `RepositoryContext` 管理。每次请求都会把路径中的名称写入 H2；`save` 返回已持久化的实体，持久化失败直接抛出异常。Service 的 `Result` 只表达业务失败。`DataSources()` 使用 `micronaut.web` 的 H2 默认值，数据保存在项目 `.norm/data/application`。
