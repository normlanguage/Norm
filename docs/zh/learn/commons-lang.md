# 87 使用已打包的库

此例从 [Norm Commons Lang 适配库](https://github.com/normlanguage/commons-lang)导入函数。内联的 `Module module()` 声明对 `commons.lang` 的直接依赖；API 来自已打包的库，而非复制到应用中的源码。

<<< ../../../norm/tests/docs/libraries/commons-lang/hello.norm{norm}

使用[开发版工具链](/zh/design/distribution-source-build)，从 Norm 仓库根目录先准备当前库源码，再运行消费者：

```sh
git clone https://github.com/normlanguage/commons-lang.git .tmp/commons-lang
norm package .tmp/commons-lang/commons/lang/module.norm --output "$HOME/.norm/cache/packages"
norm run norm/tests/docs/libraries/commons-lang/hello.norm
```

预期输出：

<<< ../../../norm/tests/docs/libraries/commons-lang/hello.out{text}

此例以本地打包的当前库验收；较早公开的库包可能采用不同的绑定格式。更完整的组合场景见[导入参考](/zh/spec/import-system)。

动手试试：修改原始标题中的空格，观察 `stringUtilsNormalizeSpace` 的效果。

精确规则：[模块依赖](/zh/spec/module-system)。
