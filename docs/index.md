---
layout: home
title: Norm：让关键语义留在源码里
titleTemplate: false
pageClass: norm-home
---

<section class="norm-hero">
  <div class="norm-hero__inner">
    <img class="norm-hero__logo" src="/brand/norm.svg" alt="Norm Logo">
    <p class="norm-hero__eyebrow">静态类型应用编程语言</p>
    <h1>把共享、失败和值流，写在代码里。</h1>
    <p class="norm-hero__lead">Norm 使用熟悉的类型前置语法，但让对象身份、结构数据、控制流结果和运行时类型信息拥有清楚且可组合的规则。</p>
    <div class="norm-hero__actions">
      <a class="norm-button norm-button--dark" href="./guide/">认识 Norm</a>
      <a class="norm-button norm-button--light" href="https://github.com/w0fv1/Norm/releases/latest">下载最新版本</a>
    </div>
    <div class="norm-code-window" aria-label="Norm 结构数据与显式值流示例">
      <div class="norm-code-window__bar"><span></span><span></span><span></span><b>profile.norm</b></div>
      <pre><code v-pre>import std.json.toJson&#10;import std.serialization.Serializable&#10;&#10;enum Audience { Adult, Minor }&#10;&#10;@Serializable()&#10;value Profile {&#10;    String name&#10;    Audience audience&#10;}&#10;&#10;String label(Audience audience) {&#10;    return switch audience {&#10;        case Adult { break "adult" }&#10;        case Minor { break "minor" }&#10;    }&#10;}&#10;&#10;main() {&#10;    Profile profile = Profile(&#10;        name: "Ada",&#10;        audience: Audience.Adult&#10;    )&#10;    printLine(label(profile.audience))&#10;    printLine(profile.toJson())&#10;}</code></pre>
    </div>
  </div>
</section>

<section class="norm-intro norm-section">
  <p class="norm-kicker">一条设计主线</p>
  <h2>读代码，就能知道程序会怎样运行。</h2>
  <p class="norm-section__lead">Norm 不靠宏、类型擦除或框架约定补充关键含义。高影响行为在声明处和调用点都有稳定、可检查的表示。</p>
  <div class="norm-feature-grid">
    <article><span class="norm-feature-number">01</span><h3>Value、Identity 与 Ref</h3><p><code>value</code> 表达结构数据，<code>class</code> 保留对象身份，<code>ref&lt;T&gt;</code> 只引用 value 的存储位置。</p></article>
    <article><span class="norm-feature-number">02</span><h3>显式值流</h3><p>命名参数保留调用含义；穷尽的 <code>switch</code> 用 <code>break value</code> 明确产生结果。</p></article>
    <article><span class="norm-feature-number">03</span><h3>运行时精确类型</h3><p>泛型实参不会被擦除。反射读取 Core metadata 和字段 ordinal，不按字符串调用 getter。</p></article>
    <article><span class="norm-feature-number">04</span><h3>受约束的扩展能力</h3><p>Extension function 保持静态解析；Annotation 分离 metadata 与拦截生命周期，不向语言引入宏系统。</p></article>
    <article><span class="norm-feature-number">05</span><h3>面向应用的边界</h3><p>文件、流式 I/O、HTTP 客户端和 JSON/XML/YAML 已使用强类型 API 与领域 Exception 接入同一运行时。</p></article>
    <article><span class="norm-feature-number">06</span><h3>一套工具链</h3><p>编译器、LSP 和 Truffle 后端共享语义模型；Native Image 交付无需预装 Java 的独立 CLI 与 VS Code 扩展。</p></article>
  </div>
</section>

<section class="norm-dark-section">
  <div class="norm-section norm-split">
    <div>
      <p class="norm-kicker">Value 与 Identity</p>
      <h2>共享发生时，不让它伪装成复制。</h2>
      <p><code>class</code> 变量保存对象引用，赋值后继续观察同一个对象。<code>value</code> 与容器保持结构语义。需要新的 class 身份时，代码必须显式调用 <code>copy()</code>。</p>
      <a class="norm-text-link" href="./language/objects">理解完整数据模型 →</a>
    </div>
    <div class="norm-compare">
      <div><small>保留同一对象身份</small><pre><code v-pre>Counter second = first&#10;second.increment()&#10;printLine(first.value)</code></pre></div>
      <div><small>创建新的顶层身份</small><pre><code v-pre>Counter second = first.copy()&#10;second.increment()&#10;printLine(first.value)</code></pre></div>
      <div><small>引用 value 的存储位置</small><pre><code v-pre>ref&lt;Integer&gt; position = &cursor&#10;*position = 12</code></pre></div>
    </div>
  </div>
</section>

<section class="norm-section norm-syntax-section">
  <p class="norm-kicker">语言能力</p>
  <h2>高级能力仍然遵守普通调用和类型规则。</h2>
  <div class="norm-showcase-grid">
    <article>
      <div><span>Reified Type Model</span><h3>运行时看到的类型，与源码写下的类型一致。</h3><p><code>reflect&lt;T&gt;()</code>、结构序列化和泛型运行时共享精确 CoreType。字段访问使用稳定 ordinal。</p><a href="./language/reflect">阅读 Annotation 与 Reflect →</a></div>
      <pre><code v-pre>Type&lt;Profile&gt; type = reflect&lt;Profile&gt;()&#10;List&lt;Field&lt;Profile&gt;&gt; fields = type.fields()&#10;String name = fields[0].name()</code></pre>
    </article>
    <article>
      <div><span>Static Extension</span><h3>点号只是清晰的调用形式，不是隐藏分派。</h3><p>Extension function 必须显式导入，真实实例方法优先；它不会修改目标类型或改变动态方法表。</p><a href="./language/functions#extension-function">阅读 Extension function →</a></div>
      <pre><code v-pre>import std.json.toJson&#10;&#10;String body = profile.toJson()&#10;String same = toJson(value: profile)</code></pre>
    </article>
  </div>
</section>

<section class="norm-section">
  <p class="norm-kicker">现在可以做什么</p>
  <h2>从语言实验走向真实应用边界。</h2>
  <div class="norm-path-grid">
    <a href="./guide/introduction"><span>LANGUAGE</span><h3>编写并运行 Norm</h3><p>使用独立 CLI、项目模块、测试 API 和 VS Code 语言服务完成开发闭环。</p><b>从快速介绍开始 →</b></a>
    <a href="./stdlib/http"><span>SYSTEM</span><h3>访问文件与网络</h3><p>通过有界流、确定性资源关闭、强类型 URI 和 HTTP request/response 连接系统能力。</p><b>查看 HTTP 与 I/O →</b></a>
    <a href="./stdlib/serialization"><span>DATA</span><h3>映射结构数据</h3><p>一套 DataMapper 契约处理 JSON、XML 与 YAML，递归映射显式标记的 value。</p><b>查看序列化 →</b></a>
  </div>
</section>

<section class="norm-blue-band">
  <div class="norm-section norm-blue-band__inner">
    <div><p class="norm-kicker">选择阅读深度</p><h2>从语言感觉，到精确规则。</h2></div>
    <p>Guide 建立整体认识，语言手册用于连续学习，规范定义编译器必须接受和执行的行为，版本记录说明当前工具链已经实现的边界。</p>
    <div class="norm-chip-list"><a href="./guide/">Guide</a><a href="./language/overview">语言手册</a><a href="./spec/language-spec">语言规范</a><a href="./versions/">当前实现</a><a href="./docs/">全部文档</a></div>
  </div>
</section>

<section class="norm-section norm-final-cta">
  <h2>先运行一段真实的 Norm 代码。</h2>
  <p>下载独立 CLI 或通用 VS Code 扩展，不需要预装 Java 或 GraalVM。</p>
  <div><a class="norm-button norm-button--blue" href="https://github.com/w0fv1/Norm/releases/latest">获取最新 Release</a><a class="norm-button norm-button--outline" href="./guide/vscode">配置 VS Code</a></div>
</section>
