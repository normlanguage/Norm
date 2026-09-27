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

const tour: Page[] = [
  ['/learn/', 'Language Tour', '语言导览'],
  ['/learn/hello', '01 Hello, Norm', '01 Hello, Norm'],
  ['/learn/bindings', '02 Values and bindings', '02 值与绑定'],
  ['/learn/functions', '03 Functions and calls', '03 函数与调用'],
  ['/learn/data-model', '04 Class, Value, and Interface', '04 Class、Value 与 Interface'],
  ['/learn/enum-switch', '05 Data enums and switch', '05 数据 Enum 与 Switch'],
  ['/learn/nullability-inference', '06 Null and inference', '06 Null 与类型推断'],
  ['/learn/collections', '07 Collections and iteration', '07 集合与迭代'],
  ['/learn/lambdas-extensions', '08 Lambdas and extensions', '08 Lambda 与 Extension'],
  ['/learn/errors', '09 Errors and exceptions', '09 错误与异常'],
  ['/learn/references', '10 References', '10 引用'],
  ['/learn/annotations', '11 Annotations', '11 Annotation'],
  ['/learn/packages-modules', '12 Packages and modules', '12 Package 与 Module'],
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
      [link('/learn/')]: [group('Language Tour', '语言导览', tour)],
      [link('/guide/')]: [group('Meet Norm', '认识 Norm', guide), group('Start learning', '开始学习', tour)],
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
