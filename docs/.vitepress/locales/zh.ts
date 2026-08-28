import type { DefaultTheme } from 'vitepress'
import { currentRelease, releaseItems } from '../release'

const tour = [
  { text: 'Language Tour', link: '/learn/' },
  { text: '01 Hello, Norm', link: '/learn/hello' },
  { text: '02 值与绑定', link: '/learn/bindings' },
  { text: '03 函数与调用', link: '/learn/functions' },
  { text: '04 Class、Value 与 Interface', link: '/learn/data-model' },
  { text: '05 数据 Enum 与 Switch', link: '/learn/enum-switch' },
  { text: '06 Null 与类型推断', link: '/learn/nullability-inference' },
  { text: '07 集合与迭代', link: '/learn/collections' },
  { text: '08 Lambda 与 Extension', link: '/learn/lambdas-extensions' },
  { text: '09 错误与异常', link: '/learn/errors' },
  { text: '10 引用', link: '/learn/references' },
  { text: '11 Annotation', link: '/learn/annotations' },
  { text: '12 Package 与 Module', link: '/learn/packages-modules' },
]

const specification = [
  { text: 'Language Reference', link: '/spec/language-spec' },
  { text: '类型系统', link: '/spec/type-system' },
  { text: '类型推断', link: '/spec/type-inference' },
  { text: 'Value 与 Identity', link: '/spec/value-identity-semantics' },
  { text: '对象模型', link: '/spec/object-model' },
  { text: '内存语义', link: '/spec/memory-semantics' },
  { text: '泛型不变性', link: '/spec/generic-variance' },
  { text: '错误模型', link: '/spec/error-model' },
  { text: 'Enum 设计', link: '/spec/enum-design' },
  { text: 'Annotation 规范', link: '/spec/annotations' },
  { text: 'Package 系统', link: '/spec/package-system' },
  { text: '导入系统', link: '/spec/import-system' },
  { text: '模块系统', link: '/spec/module-system' },
  { text: '编译器设计', link: '/spec/compiler-design' },
]

const grammar = [
  { text: '语法索引', link: '/spec/grammar/overview' },
  { text: '词法结构', link: '/spec/grammar/lexical' },
  { text: '关键字', link: '/spec/grammar/keywords' },
  { text: '字面量', link: '/spec/grammar/literals' },
  { text: '声明', link: '/spec/grammar/declarations' },
  { text: '类型', link: '/spec/grammar/types' },
  { text: '表达式', link: '/spec/grammar/expressions' },
  { text: '语句', link: '/spec/grammar/statements' },
  { text: '函数', link: '/spec/grammar/functions' },
  { text: '类', link: '/spec/grammar/classes' },
  { text: '接口', link: '/spec/grammar/interfaces' },
  { text: '泛型', link: '/spec/grammar/generics' },
  { text: '模块描述', link: '/spec/grammar/modules' },
  { text: '循环', link: '/spec/grammar/loops' },
  { text: 'Switch', link: '/spec/grammar/switch' },
  { text: '模式', link: '/spec/grammar/patterns' },
  { text: '操作符优先级', link: '/spec/grammar/operators-precedence' },
  { text: 'ref 引用', link: '/spec/grammar/references' },
]

const stdlib = [
  { text: '标准库概览', link: '/stdlib/overview' },
  { text: '输出', link: '/stdlib/output-api' },
  { text: 'I/O 基础', link: '/stdlib/io' },
  { text: 'String', link: '/stdlib/string' },
  { text: 'Array', link: '/stdlib/array' },
  { text: 'Collections', link: '/stdlib/collections' },
  { text: 'Map', link: '/stdlib/map' },
  { text: 'Set', link: '/stdlib/set' },
  { text: 'Math', link: '/stdlib/math' },
  { text: 'Time', link: '/stdlib/time' },
  { text: 'Filesystem', link: '/stdlib/filesystem' },
  { text: 'HTTP', link: '/stdlib/http' },
  { text: 'Serialization', link: '/stdlib/serialization' },
  { text: 'JSON', link: '/stdlib/json-api' },
  { text: 'XML', link: '/stdlib/xml-api' },
  { text: 'YAML', link: '/stdlib/yaml-api' },
  { text: 'Validation', link: '/stdlib/validation-api' },
  { text: 'Testing', link: '/stdlib/testing-api' },
]

export const zhTheme: DefaultTheme.Config = {
  nav: [
    { text: '学习', link: '/learn/', activeMatch: '^/learn/' },
    { text: '语言', link: '/guide/', activeMatch: '^/guide/' },
    {
      text: '参考',
      activeMatch: '^/(spec|stdlib)/',
      items: [
        { text: '语言参考', link: '/spec/language-spec' },
        { text: '标准库', link: '/stdlib/overview' },
      ],
    },
    { text: '工具', link: '/tooling/', activeMatch: '^/tooling/' },
    {
      text: '项目',
      activeMatch: '^/(design|versions)/|^/status$',
      items: [
        { text: '编译器设计', link: '/design/' },
        { text: '当前状态', link: '/status' },
        { text: '版本记录', link: '/versions/' },
      ],
    },
  ],
  socialLinks: [{ icon: 'github', link: 'https://github.com/w0fv1/Norm' }],
  sidebar: {
    '/learn/': [{ text: 'Language Tour', items: tour }],
    '/guide/': [
      { text: '开始', items: [
        { text: 'Language', link: '/guide/' },
        { text: '语言哲学', link: '/guide/philosophy' },
        { text: '设计原则', link: '/guide/design-principles' },
        { text: '语言设计白皮书', link: '/guide/design-whitepaper' },
        { text: '比较、取舍与方向', link: '/guide/comparison-and-future' },
      ]},
      { text: '开始学习', items: tour },
    ],
    '/spec/': [
      { text: 'Language Reference', items: specification },
      { text: '语法参考', collapsed: true, items: grammar },
      { text: '形式化规范', collapsed: true, items: [
        { text: '语义', link: '/spec/formal/semantics' },
        { text: '求值', link: '/spec/formal/evaluation' },
        { text: '完整类型系统', link: '/spec/formal/type-system-complete' },
        { text: '泛型推断', link: '/spec/formal/generic-inference' },
        { text: '完整泛型规范', link: '/spec/formal/generics-complete' },
      ]},
    ],
    '/stdlib/': [{ text: '标准库', items: stdlib }],
    '/tooling/': [{ text: 'Tooling', items: [
      { text: '工具链概览', link: '/tooling/' },
      { text: 'VS Code', link: '/guide/vscode' },
      { text: '当前状态', link: '/status' },
    ]}],
    '/versions/': [{ text: '版本记录', items: [
      { text: '版本索引', link: '/versions/' },
      ...releaseItems('/versions'),
    ]}],
    '/design/': [
      { text: '设计入口', items: [
        { text: 'Compiler Design', link: '/design/' },
        { text: '编译器架构', link: '/spec/compiler-design' },
      ]},
      { text: '实现与规划', items: [
        ...releaseItems('/versions'),
        { text: '实现策略决议', link: '/design/implementation-strategy' },
        { text: '工具链开发规范', link: '/design/toolchain-development' },
        { text: '系统运行时架构', link: '/design/system-runtime' },
        { text: '编译器引导计划', link: '/design/bootstrap-plan' },
        { text: '技术方案', link: '/design/technical-plan' },
        { text: '项目路线图', link: '/design/roadmap' },
      ]},
      { text: '项目约束', collapsed: true, items: [
        { text: '性能目标', link: '/design/performance-goals' },
        { text: '兼容性', link: '/design/compatibility' },
        { text: '语言演进', link: '/design/language-evolution' },
        { text: '发布流程', link: '/design/release-process' },
        { text: '治理', link: '/design/governance' },
      ]},
    ],
  },
  outline: { level: [2, 3], label: '本页内容' },
  docFooter: { prev: '上一页', next: '下一页' },
  lastUpdated: { text: '最后更新于' },
  returnToTopLabel: '返回顶部',
  sidebarMenuLabel: '目录',
  darkModeSwitchLabel: '外观',
  langMenuLabel: '切换语言',
  footer: { message: `Norm ${currentRelease} 开发线`, copyright: 'Norm Project' },
}

