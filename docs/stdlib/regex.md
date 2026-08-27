# Regex

Regex 是编译后的正则表达式值。动态 pattern 无效时抛出 `RegexException`。

```norm
Regex compiled = Regex.compile(
    pattern: "^[a-z][a-z0-9_]{2,31}$",
    options: RegexOptions(caseInsensitive: false)
)
```

## 匹配

```norm
Match? match = regex.find(input: text)
Boolean valid = regex.matchesEntire(input: text)
List<String> parts = regex.split(input: text, limit: 10)
```

Match 保存整个匹配范围和命名/编号 group。未参与匹配的可选 group 返回 `String?`，不使用空字符串冒充缺失。

## 安全与 Unicode

实现必须文档化所支持的正则语法与 Unicode 版本。服务端处理不可信 pattern 或超长输入时应提供执行预算、超时或使用保证线性时间的引擎。

替换 API 区分字面 replacement 与模板 replacement，避免 `$1` 一类文本被意外解释。需要程序计算替换值时使用显式函数回调。

Regex pattern 不作为语言字面量，避免为核心 lexer 增加另一套转义规则。
