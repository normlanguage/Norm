import type { DefaultTheme } from 'vitepress'
import { releaseItems } from '../release'

type Language = 'en' | 'zh'
type Page = readonly [path: string, english: string, chinese: string]

const language: Page[] = [
  ['/language/overview', 'Handbook overview', '语言手册概览'],
  ['/language/types', 'Types and null', '类型与 Null'],
  ['/language/objects', 'Value and identity', '值与身份'],
  ['/language/control-flow', 'Control-flow expressions', '控制流表达式'],
  ['/language/generics', 'Reified generics', '具现化泛型'],
]

const tourEntry: Page[] = [['/learn/', 'Learning path', '学习路径']]

const tourBasics: Page[] = [
  ['/learn/hello', '01 Program entry', '01 程序入口'],
  ['/learn/bindings', '02 Explicit types and assignment', '02 显式类型与赋值'],
  ['/learn/inference', '03 Type inference', '03 类型推断'],
  ['/learn/interpolation', '04 String interpolation', '04 字符串插值'],
  ['/learn/operators', '05 Operators and comparisons', '05 运算与比较'],
  ['/learn/conditionals', '06 Conditional execution', '06 条件执行'],
]

const tourFunctions: Page[] = [
  ['/learn/functions', '07 Functions and explicit returns', '07 函数与显式返回'],
  ['/learn/named-arguments', '08 Named arguments', '08 命名参数'],
  ['/learn/default-arguments', '09 Default arguments', '09 默认参数'],
  ['/learn/argument-shorthand', '10 Argument shorthand', '10 同名实参简写'],
  ['/learn/if-results', '11 if as a result', '11 if 的返回值'],
  ['/learn/final-expression', '12 Final-expression returns', '12 末尾表达式返回'],
]

const tourCollections: Page[] = [
  ['/learn/lists', '13 Lists', '13 列表'],
  ['/learn/iteration', '14 Iteration', '14 迭代'],
  ['/learn/conditional-loops', '15 Conditional loops', '15 条件循环'],
  ['/learn/break', '16 Break', '16 结束循环'],
  ['/learn/continue', '17 Continue', '17 跳过本轮'],
  ['/learn/arrays', '18 Arrays', '18 数组'],
  ['/learn/maps', '19 Maps', '19 映射'],
  ['/learn/sets', '20 Sets', '20 集合'],
  ['/learn/collection-values', '21 Collection value semantics', '21 集合的值语义'],
  ['/learn/collection-for', '22 Collection `for` elements', '22 集合字面量中的 `for`'],
  ['/learn/collection-if', '23 Collection `if` elements', '23 集合字面量中的 `if`'],
  ['/learn/spread', '24 Spread in collections', '24 集合展开'],
]

const tourData: Page[] = [
  ['/learn/value-declarations', '25 Value declarations', '25 值声明'],
  ['/learn/value-equality', '26 Value equality', '26 值相等'],
  ['/learn/class-declarations', '27 Class declarations', '27 类声明'],
  ['/learn/identity', '28 Class identity', '28 类的身份'],
  ['/learn/copying', '29 Copying a class', '29 复制类对象'],
  ['/learn/constructors', '30 Explicit constructors', '30 显式构造函数'],
  ['/learn/computed-properties', '31 Computed properties', '31 计算属性'],
  ['/learn/fluent-methods', '32 Fluent methods', '32 链式方法'],
]

const tourMatching: Page[] = [
  ['/learn/nullable-values', '33 Nullable values', '33 可空值'],
  ['/learn/flow-narrowing', '34 Flow narrowing', '34 控制流收窄'],
  ['/learn/safe-access', '35 Safe access', '35 安全访问'],
  ['/learn/fallback', '36 Null fallback', '36 空值回退'],
  ['/learn/plain-enums', '37 Plain enums', '37 普通枚举'],
  ['/learn/data-enums', '38 Data enums', '38 带数据的枚举'],
  ['/learn/switch-results', '39 Switch results', '39 Switch 返回值'],
  ['/learn/exhaustive-matching', '40 Exhaustive matching', '40 穷尽匹配'],
  ['/learn/nested-patterns', '41 Nested patterns', '41 嵌套模式'],
]

const tourAbstractions: Page[] = [
  ['/learn/interface-declarations', '42 Interface declarations', '42 接口声明'],
  ['/learn/interface-implementation', '43 Implementing an interface', '43 实现接口'],
  ['/learn/default-implementations', '44 Default interface methods', '44 接口默认方法'],
  ['/learn/inheritance', '45 Class inheritance', '45 类继承'],
  ['/learn/overrides', '46 Overriding methods', '46 重写方法'],
  ['/learn/generic-types', '47 Generic types', '47 泛型类型'],
  ['/learn/generic-functions', '48 Generic functions', '48 泛型函数'],
  ['/learn/generic-inference', '49 Generic inference', '49 泛型推断'],
  ['/learn/retained-generic-matching', '50 Matching generic types', '50 匹配泛型类型'],
  ['/learn/overloads', '51 Function overloads', '51 函数重载'],
]

const tourAdvancedFunctions: Page[] = [
  ['/learn/function-values', '52 Function values', '52 函数值'],
  ['/learn/lambdas', '53 Lambdas', '53 Lambda 表达式'],
  ['/learn/capture', '54 Capturing values', '54 捕获外部值'],
  ['/learn/method-references', '55 Bound method references', '55 绑定方法引用'],
  ['/learn/extensions', '56 Extension functions', '56 扩展函数'],
  ['/learn/trailing-callbacks', '57 Trailing callbacks', '57 尾随回调'],
  ['/learn/named-callbacks', '58 Named callback parameters', '58 具名回调参数'],
  ['/learn/block-call-chains', '59 Chaining block calls', '59 串联代码块调用'],
]

const tourProjects: Page[] = [
  ['/learn/imports', '60 Imports', '60 导入'],
  ['/learn/modules', '61 Modules', '61 模块'],
  ['/learn/packages', '62 Packages', '62 包'],
  ['/learn/visibility', '63 Visibility', '63 可见性'],
  ['/learn/dependencies', '64 Local dependencies', '64 本地依赖'],
  ['/learn/testing', '65 Tests', '65 测试'],
  ['/learn/exceptions', '66 Exception handling', '66 异常处理'],
  ['/learn/cleanup', '67 Cleanup with finally', '67 清理资源'],
]

const tourMetadata: Page[] = [
  ['/learn/type-references', '68 Type declaration references', '68 类型声明引用'],
  ['/learn/field-references', '69 Field declaration references', '69 字段声明引用'],
  ['/learn/function-references', '70 Function declaration references', '70 函数声明引用'],
  ['/learn/annotation-targets', '71 Annotation targets', '71 注解目标'],
  ['/learn/annotation-retention', '72 Annotation retention', '72 注解保留策略'],
  ['/learn/runtime-annotations', '73 Runtime annotation metadata', '73 运行时注解元数据'],
  ['/learn/function-interception', '74 Function interception', '74 函数拦截'],
  ['/learn/interceptor-order', '75 Interceptor entry and exit', '75 拦截器的进入与退出'],
  ['/learn/document-annotation', '76 Documenting declarations for agents', '76 给 Agent 使用的声明文档'],
]

const tourReactive: Page[] = [
  ['/learn/lexical-references', '77 Lexical references', '77 词法引用'],
  ['/learn/field-handles', '78 Field handles', '78 字段句柄'],
  ['/learn/field-subscriptions', '79 Field subscriptions', '79 字段订阅'],
  ['/learn/collection-changes', '80 Collection change notifications', '80 集合变更通知'],
  ['/learn/resource-ownership', '81 Resource ownership', '81 资源所有权'],
  ['/learn/result-builders', '82 Result builders', '82 结果构建器'],
  ['/learn/builder-control-flow', '83 Control flow in result builders', '83 结果构建器中的控制流'],
]

const tourLibraries: Page[] = [
  ['/learn/json-roundtrip', '84 JSON value roundtrip', '84 JSON 值往返转换'],
  ['/learn/file-write', '85 Writing and reading a file', '85 写入并读取文件'],
  ['/learn/file-failures', '86 Typed file failures', '86 类型化的文件错误'],
  ['/learn/commons-lang', '87 A packaged library', '87 使用已打包的库'],
]

const guide: Page[] = [
  ['/guide/', 'Meet Norm', '认识 Norm'],
  ['/guide/philosophy', 'Language philosophy', '语言哲学'],
  ['/guide/design-principles', 'Design principles', '设计原则'],
  ['/guide/design-whitepaper', 'Language design whitepaper', '语言设计白皮书'],
  ['/guide/comparison-and-future', 'Comparison and direction', '比较、取舍与方向'],
  ['/guide/vscode', 'VS Code development experience', 'VS Code 开发体验'],
]

const specification: Page[] = [
  ['/spec/language-spec', 'Language Reference', '语言参考'],
  ['/spec/type-system', 'Type system', '类型系统'],
  ['/spec/type-inference', 'Type inference', '类型推断'],
  ['/spec/value-identity-semantics', 'Value and identity', 'Value 与 Identity'],
  ['/spec/object-model', 'Object model', '对象模型'],
  ['/spec/memory-semantics', 'Memory semantics', '内存语义'],
  ['/spec/generic-variance', 'Generic invariance', '泛型不变性'],
  ['/spec/error-model', 'Error model', '错误模型'],
  ['/spec/enum-design', 'Enum design', 'Enum 设计'],
  ['/spec/annotations', 'Annotation specification', 'Annotation 规范'],
  ['/spec/declaration-references', 'Declaration references and reflection', '声明引用与反射'],
  ['/spec/execution-context', 'Execution context', '执行上下文'],
  ['/spec/package-system', 'Package system', 'Package 系统'],
  ['/spec/import-system', 'Import system', '导入系统'],
  ['/spec/module-system', 'Module system', '模块系统'],
  ['/spec/compiler-design', 'Compiler design', '编译器设计'],
]

const grammar: Page[] = [
  ['/spec/grammar/overview', 'Grammar index', '语法索引'],
  ['/spec/grammar/lexical', 'Lexical structure', '词法结构'],
  ['/spec/grammar/keywords', 'Keywords', '关键字'],
  ['/spec/grammar/literals', 'Literals', '字面量'],
  ['/spec/grammar/declarations', 'Declarations', '声明'],
  ['/spec/grammar/types', 'Types', '类型'],
  ['/spec/grammar/expressions', 'Expressions', '表达式'],
  ['/spec/grammar/statements', 'Statements', '语句'],
  ['/spec/grammar/functions', 'Functions', '函数'],
  ['/spec/grammar/classes', 'Classes', '类'],
  ['/spec/grammar/interfaces', 'Interfaces', '接口'],
  ['/spec/grammar/generics', 'Generics', '泛型'],
  ['/spec/grammar/modules', 'Module descriptions', '模块描述'],
  ['/spec/grammar/loops', 'Loops', '循环'],
  ['/spec/grammar/switch', 'Switch', 'Switch'],
  ['/spec/grammar/patterns', 'Patterns', '模式'],
  ['/spec/grammar/operators-precedence', 'Operator precedence', '操作符优先级'],
  ['/spec/grammar/references', 'References', 'ref 引用'],
]

const formal: Page[] = [
  ['/spec/formal/semantics', 'Semantics', '语义'],
  ['/spec/formal/evaluation', 'Evaluation', '求值'],
  ['/spec/formal/type-system-complete', 'Complete type system', '完整类型系统'],
  ['/spec/formal/generic-inference', 'Generic inference', '泛型推断'],
  ['/spec/formal/generics-complete', 'Complete generics', '完整泛型规范'],
]

const stdlib: Page[] = [
  ['/stdlib/overview', 'Standard library overview', '标准库概览'],
  ['/stdlib/samples', 'Library samples', '库示例'],
  ['/stdlib/api', 'API browser', 'API 浏览器'],
  ['/stdlib/output-api', 'Output', '输出'],
  ['/stdlib/cli', 'CLI and processes', '命令行与进程'],
  ['/stdlib/io', 'I/O fundamentals', 'I/O 基础'],
  ['/stdlib/string', 'String', 'String'],
  ['/stdlib/array', 'Array', 'Array'],
  ['/stdlib/collections', 'Collections', 'Collections'],
  ['/stdlib/map', 'Map', 'Map'],
  ['/stdlib/set', 'Set', 'Set'],
  ['/stdlib/math', 'Math', 'Math'],
  ['/stdlib/time', 'Time', 'Time'],
  ['/stdlib/filesystem', 'Filesystem', 'Filesystem'],
  ['/stdlib/http', 'HTTP', 'HTTP'],
  ['/stdlib/websocket', 'WebSocket', 'WebSocket'],
  ['/stdlib/configuration', 'Configuration', 'Configuration'],
  ['/stdlib/serialization', 'Serialization', 'Serialization'],
  ['/stdlib/json-api', 'JSON', 'JSON'],
  ['/stdlib/xml-api', 'XML', 'XML'],
  ['/stdlib/yaml-api', 'YAML', 'YAML'],
  ['/stdlib/validation-api', 'Validation', 'Validation'],
  ['/stdlib/testing-api', 'Testing', 'Testing'],
]

const tooling: Page[] = [
  ['/tooling/', 'Tooling overview', '工具链概览'],
  ['/tooling/application-build', 'Application builds', '应用构建'],
  ['/tooling/library-samples', 'Library samples', '库示例'],
  ['/tooling/api-documentation', 'API documentation export', 'API 文档导出'],
  ['/tooling/agent', 'Agent development', 'Agent 开发入口'],
  ['/tooling/verification', 'Checking and testing', '检查与测试'],
  ['/tooling/semantic-query', 'Semantic queries', '语义查询'],
  ['/tooling/rename-preview', 'Semantic refactor preview', '语义重构预检'],
  ['/tooling/agent-benchmark', 'Agent task benchmark', 'Agent 任务基准'],
  ['/guide/vscode', 'VS Code', 'VS Code'],
  ['/status', 'Current status', '当前状态'],
]

const design: Page[] = [
  ['/design/', 'Compiler design', 'Compiler Design'],
  ['/spec/compiler-design', 'Compiler architecture', '编译器架构'],
  ['/design/implementation-strategy', 'Implementation strategy', '实现策略决议'],
  ['/design/toolchain-development', 'Toolchain development', '工具链开发规范'],
  ['/design/agent-tooling', 'Agent tool design', 'Agent 工具设计'],
  ['/design/system-runtime', 'System runtime architecture', '系统运行时架构'],
  ['/design/bootstrap-plan', 'Compiler bootstrap plan', '编译器引导计划'],
  ['/design/technical-plan', 'Technical plan', '技术方案'],
  ['/design/roadmap', 'Roadmap', '项目路线图'],
  ['/design/business-outlook', 'Business outlook', '商业计划展望'],
]

const constraints: Page[] = [
  ['/design/performance-goals', 'Performance goals', '性能目标'],
  ['/design/compatibility', 'Compatibility', '兼容性'],
  ['/design/language-evolution', 'Language evolution', '语言演进'],
  ['/design/release-process', 'Release process', '发布流程'],
  ['/design/governance', 'Governance', '治理'],
]

export function navigationFor(languageCode: Language): Pick<DefaultTheme.Config, 'nav' | 'sidebar'> {
  const zh = languageCode === 'zh'
  const prefix = zh ? '/zh' : ''
  const label = (english: string, chinese: string) => zh ? chinese : english
  const link = (path: string) => `${prefix}${path}`
  const items = (pages: Page[]) => pages.map(([path, english, chinese]) => ({ text: label(english, chinese), link: link(path) }))
  const group = (english: string, chinese: string, pages: Page[], collapsed = false) => ({ text: label(english, chinese), collapsed, items: items(pages) })
  return {
    nav: [
      { text: label('Learn', '学习'), link: link('/learn/'), activeMatch: `^${prefix}/learn/` },
      { text: label('Language', '语言'), link: link('/guide/'), activeMatch: `^${prefix}/(guide|language)/` },
      { text: label('Reference', '参考'), activeMatch: `^${prefix}/(spec|stdlib)/`, items: [
        { text: label('Language Reference', '语言参考'), link: link('/spec/language-spec') },
        { text: label('Standard Library', '标准库'), link: link('/stdlib/overview') },
      ]},
      { text: label('Tooling', '工具'), link: link('/tooling/'), activeMatch: `^${prefix}/tooling/` },
      { text: label('Project', '项目'), activeMatch: `^${prefix}/(design|versions)/|^${prefix}/status$`, items: [
        { text: label('Compiler design', '编译器设计'), link: link('/design/') },
        { text: label('Current status', '当前状态'), link: link('/status') },
        { text: label('Releases', '版本记录'), link: link('/versions/') },
      ]},
    ],
    sidebar: {
      [link('/language/')]: [group('Language handbook', '语言手册', language)],
      [link('/learn/')]: [
        group('Learning path', '学习路径', tourEntry),
        group('Write and run a program', '编写并运行程序', tourBasics),
        group('Organize calculations with functions', '用函数组织计算', tourFunctions),
        group('Work with collections and control flow', '集合与控制流', tourCollections),
        group('Model values and entities', '值与实体建模', tourData),
        group('Handle absence and match states', '缺失值与状态匹配', tourMatching),
        group('Compose abstractions', '组合抽象', tourAbstractions),
        group('Functions as values', '函数作为值', tourAdvancedFunctions),
        group('Build a project', '构建项目', tourProjects),
        group('Describe declarations', '描述声明', tourMetadata),
        group('Observe and build', '观察与构建', tourReactive),
        group('Use libraries', '使用库', tourLibraries),
      ],
      [link('/guide/')]: [group('Meet Norm', '认识 Norm', guide), group('Start learning', '开始学习', [...tourEntry, ...tourBasics])],
      [link('/spec/')]: [group('Language Reference', '语言参考', specification), group('Grammar', '语法参考', grammar, true), group('Formal specification', '形式化规范', formal, true)],
      [link('/stdlib/')]: [group('Standard Library', '标准库', stdlib)],
      [link('/tooling/')]: [group('Tooling', '工具链', tooling)],
      [link('/versions/')]: [{ text: label('Releases', '版本记录'), items: [
        { text: label('Version index', '版本索引'), link: link('/versions/') },
        ...releaseItems(link('/versions')),
      ]}],
      [link('/design/')]: [
        { text: label('Design', '设计入口'), items: items(design.slice(0, 2)) },
        { text: label('Implementation and plans', '实现与规划'), items: [...releaseItems(link('/versions')), ...items(design.slice(2))] },
        group('Project constraints', '项目约束', constraints, true),
      ],
    },
  }
}
