# 库示例

下列仓库各自维护示例源码与使用说明。下面按用途指向源码所有者；软件包准备步骤与运行命令以各仓库的示例说明为准。部分示例需要本地构建的开发版软件包，通用前提见[库示例准备指南](/zh/tooling/library-samples)。简短教学包括 [JSON 往返转换](/zh/learn/json-roundtrip)、[文件读写](/zh/learn/file-write)、[文件错误](/zh/learn/file-failures)和 [Commons Lang 入门](/zh/learn/commons-lang)。

## 文本与文档

| 库 | 用途 | 示例 |
| --- | --- | --- |
| [commons-lang](https://github.com/normlanguage/commons-lang) | 规范化并变换文本 | [示例](https://github.com/normlanguage/commons-lang/blob/main/samples/README.zh-CN.md) |
| [commons-io](https://github.com/normlanguage/commons-io) | 文件操作与版本比较 | [示例](https://github.com/normlanguage/commons-io/blob/main/samples/README.zh-CN.md) |
| [jsoup-jsoup](https://github.com/normlanguage/jsoup-jsoup) | 提取链接并清理 HTML | [示例](https://github.com/normlanguage/jsoup-jsoup/blob/main/samples/README.zh-CN.md) |

## 数据与集合

| 库 | 用途 | 示例 |
| --- | --- | --- |
| [org-json](https://github.com/normlanguage/org-json) | 解析并更新 JSON 对象 | [示例](https://github.com/normlanguage/org-json/blob/main/samples/README.zh-CN.md) |
| [joda-time](https://github.com/normlanguage/joda-time) | 计算跨日历边界的日期 | [示例](https://github.com/normlanguage/joda-time/blob/main/samples/README.zh-CN.md) |
| [fastutil-collections](https://github.com/normlanguage/fastutil-collections) | 使用原始类型集合计数 | [示例](https://github.com/normlanguage/fastutil-collections/blob/main/samples/README.zh-CN.md) |
| [eclipse-collections](https://github.com/normlanguage/eclipse-collections) | 筛选并汇总集合 | [示例](https://github.com/normlanguage/eclipse-collections/blob/main/samples/README.zh-CN.md) |
| [guava-core](https://github.com/normlanguage/guava-core) | 拆分并拼接文本集合 | [示例](https://github.com/normlanguage/guava-core/blob/main/samples/README.zh-CN.md) |
| [caffeine-cache](https://github.com/normlanguage/caffeine-cache) | 有界缓存与失效 | [示例](https://github.com/normlanguage/caffeine-cache/blob/main/samples/README.zh-CN.md) |

## 应用与集成

| 库 | 用途 | 示例 |
| --- | --- | --- |
| [di](https://github.com/normlanguage/di) | 组装依赖注入服务 | [示例](https://github.com/normlanguage/di/blob/main/samples/README.zh-CN.md) |
| [junit-jupiter](https://github.com/normlanguage/junit-jupiter) | 运行 JUnit 支持的 Norm 测试 | [示例](https://github.com/normlanguage/junit-jupiter/blob/main/samples/README.zh-CN.md) |
| [micronaut-test](https://github.com/normlanguage/micronaut-test) | 测试 Micronaut 容器与替换注入 | [示例](https://github.com/normlanguage/micronaut-test/blob/main/samples/README.zh-CN.md) |
| [micronaut-aop](https://github.com/normlanguage/micronaut-aop) | 拦截服务调用 | [示例](https://github.com/normlanguage/micronaut-aop/blob/main/samples/README.zh-CN.md) |
| [micronaut-serde-jackson](https://github.com/normlanguage/micronaut-serde-jackson) | 序列化类型化消息 | [示例](https://github.com/normlanguage/micronaut-serde-jackson/blob/main/samples/README.zh-CN.md) |
| [micronaut-validation](https://github.com/normlanguage/micronaut-validation) | 验证无效输入 | [示例](https://github.com/normlanguage/micronaut-validation/blob/main/samples/README.zh-CN.md) |
| [micronaut-views-jstachio](https://github.com/normlanguage/micronaut-views-jstachio) | 渲染 HTML 模板 | [示例](https://github.com/normlanguage/micronaut-views-jstachio/blob/main/samples/README.zh-CN.md) |
| [orm](https://github.com/normlanguage/orm) | 在 H2 中保存、查询并更新实体 | [示例](https://github.com/normlanguage/orm/blob/main/samples/README.zh-CN.md) |
| [micronaut-data-jdbc](https://github.com/normlanguage/micronaut-data-jdbc) | 通过仓库插入并查询 H2 记录 | [示例](https://github.com/normlanguage/micronaut-data-jdbc/blob/main/samples/README.zh-CN.md) |
| [reactor-core](https://github.com/normlanguage/reactor-core) | 变换有限的响应式数据流 | [示例](https://github.com/normlanguage/reactor-core/blob/main/samples/README.zh-CN.md) |
| [ui](https://github.com/normlanguage/ui) | 创建计数窗口 | [示例](https://github.com/normlanguage/ui/blob/main/samples/README.zh-CN.md) |

UI 计数示例需要图形会话；依赖和运行命令以该库的示例指南为准。

## HTTP 与 Agent

| 库 | 用途 | 示例 |
| --- | --- | --- |
| [micronaut-web](https://github.com/normlanguage/micronaut-web) | 提供本地 HTTP 路由与表单 | [示例](https://github.com/normlanguage/micronaut-web/blob/main/samples/README.zh-CN.md) |
| [micronaut-websocket](https://github.com/normlanguage/micronaut-websocket) | 交换本地 WebSocket 消息 | [示例](https://github.com/normlanguage/micronaut-websocket/blob/main/samples/README.zh-CN.md) |
| [micronaut-security](https://github.com/normlanguage/micronaut-security) | 对比匿名请求的允许与拒绝 | [示例](https://github.com/normlanguage/micronaut-security/blob/main/samples/README.zh-CN.md) |
| [micronaut-http-client](https://github.com/normlanguage/micronaut-http-client) | 请求本地 HTTP 服务 | [示例](https://github.com/normlanguage/micronaut-http-client/blob/main/samples/README.zh-CN.md) |
| [micronaut-management](https://github.com/normlanguage/micronaut-management) | 查询健康检查端点 | [示例](https://github.com/normlanguage/micronaut-management/blob/main/samples/README.zh-CN.md) |
| [okhttp-client](https://github.com/normlanguage/okhttp-client) | 调用本地 HTTP 测试服务 | [示例](https://github.com/normlanguage/okhttp-client/blob/main/samples/README.zh-CN.md) |
| [openai](https://github.com/normlanguage/openai) | 调用本地 OpenAI 兼容测试服务并分派工具 | [示例](https://github.com/normlanguage/openai/blob/main/samples/README.zh-CN.md) |

完整的 GUI Todo 与 Web 留言板由 [`norm hello`](/zh/tooling/#从示例开始) 生成。
