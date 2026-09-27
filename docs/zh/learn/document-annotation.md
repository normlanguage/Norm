# 76 给 Agent 使用的声明文档

`@Document` 将说明附在 `greet` 声明上。语义查询从编译器的同一模型读取该说明，让 Agent 在受检查的签名旁看到用途。

<<< ../../../norm/tests/docs/tour/document_annotation.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/document_annotation.out{text}

从仓库根目录查询声明：

```sh
norm query norm/tests/docs/tour/document_annotation.norm greet
```

查询结果中 `query.context.declaration.documentation` 为 `Returns a greeting for the named person.`。

动手试试：修改说明文本，再运行 `norm query`，观察 documentation 字段。

精确规则：[参考](/zh/tooling/semantic-query)。
