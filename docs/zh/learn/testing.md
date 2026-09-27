# 65 测试

`@Test` 将 `doublesSeven` 关联到 `double.function`。`require` 检查行为；`norm test` 独立发现并运行此测试。

<<< ../../../norm/tests/docs/tour/testing.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/testing.out{text}

从仓库根目录运行该测试：

```sh
norm test norm/tests/docs/tour/testing.norm --format json
```

JSON 结果应报告发现 1 项、通过 1 项。

动手试试：把预期值 14 改成 15，再运行 `norm test` 查看失败。

精确规则：[参考](/zh/tooling/verification)。
