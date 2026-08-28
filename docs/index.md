---
layout: home
title: Norm：熟悉的语法，明确的语义
titleTemplate: false
pageClass: norm-home
---

<script setup>
import heroExample from '../norm/tests/docs/tour/05_enum_switch.norm?raw'
</script>

<section class="norm-hero">
  <div class="norm-hero__inner">
    <img class="norm-hero__logo" src="/brand/norm.svg" alt="Norm Logo">
    <p class="norm-hero__eyebrow">静态类型应用编程语言</p>
    <h1><span>熟悉的语法，</span><span>明确的语义。</span></h1>
    <p class="norm-hero__lead">用 <code>class</code> 表达身份，用 <code>value</code> 表达值，用 <code>enum</code> 表达状态，用 <code>interface</code> 表达能力，用 <code>ref</code> 表达受控的可变访问。</p>
    <div class="norm-hero__actions">
      <a class="norm-button norm-button--dark" href="./learn/">开始学习</a>
      <a class="norm-button norm-button--light" href="./guide/">了解 Norm</a>
    </div>
    <div class="norm-code-window" aria-label="经过编译和运行验证的数据枚举示例">
      <div class="norm-code-window__bar"><span></span><span></span><span></span><b>delivery.norm · N-42</b></div>
      <pre><code>{{ heroExample }}</code></pre>
    </div>
  </div>
</section>

<section class="norm-intro norm-section">
  <p class="norm-kicker">一个类型模型</p>
  <h2>不是所有数据都应该是同一种对象。</h2>
  <p class="norm-section__lead">语言构造直接说明数据是否具有身份、能否改变、如何组合，以及调用者可以依赖什么。</p>
  <div class="norm-feature-grid norm-semantics-grid">
    <article><span class="norm-feature-number">CLASS</span><h3>Identity</h3><p>具有身份和可变状态的实体；赋值、传参和返回保留同一对象。</p></article>
    <article><span class="norm-feature-number">VALUE</span><h3>Data</h3><p>由字段内容定义的值；构造后字段不可重新赋值。</p></article>
    <article><span class="norm-feature-number">ENUM</span><h3>Alternatives</h3><p>有限且可携带数据的状态集合，由模式解构。</p></article>
    <article><span class="norm-feature-number">INTERFACE</span><h3>Capability</h3><p>名义化能力和替换契约，不改变底层数据类别。</p></article>
    <article><span class="norm-feature-number">REF</span><h3>Controlled aliasing</h3><p>受词法生命周期约束的 value 存储位置引用。</p></article>
  </div>
</section>

<section class="norm-dark-section">
  <div class="norm-section norm-split">
    <div>
      <p class="norm-kicker">Data Enum 与 Switch</p>
      <h2>每一种状态都由编译器检查。</h2>
      <p><code>switch</code> 是表达式。Variant 可以携带数据，模式可以递归嵌套；遗漏分支和不可达分支都会产生诊断。</p>
      <a class="norm-text-link" href="./learn/enum-switch">学习数据 Enum 与模式匹配 →</a>
    </div>
    <div class="norm-compare">
      <div><small>显式产生结果</small><pre v-pre><code>case Sent(String code) {&#10;  break code&#10;}</code></pre></div>
      <div><small>覆盖剩余状态</small><pre v-pre><code>case Failed(_) {&#10;  break "failed"&#10;}</code></pre></div>
      <div><small>没有隐式 Fallthrough</small><pre v-pre><code>每个 case 独占执行&#10;被匹配值只求值一次</code></pre></div>
    </div>
  </div>
</section>

<section class="norm-section norm-syntax-section">
  <p class="norm-kicker">类型服务于理解</p>
  <h2>能从上下文确定时省略，语义不足时拒绝猜测。</h2>
  <div class="norm-showcase-grid norm-showcase-grid--three">
    <article><div><span>NULL SAFETY</span><h3>缺失进入类型。</h3><p><code>String?</code>、<code>?.</code> 和 <code>??</code> 分别表达可空值、安全访问和回退路径。</p></div><pre v-pre><code>Integer size(String? text) {&#10;  return text?.graphemeSize() ?? 0&#10;}</code></pre></article>
    <article><div><span>INFERENCE</span><h3>推断使用期望类型。</h3><p>集合字面量和泛型构造器利用赋值目标与实参；失败时不会退化成动态类型。</p></div><pre v-pre><code>Array&lt;Integer&gt; fixed = [1, 2, 3]&#10;List&lt;Integer&gt; dynamic = [1, 2, 3]&#10;List&lt;Pair&lt;Integer, String&gt;&gt; pairs = List&lt;&gt;()</code></pre></article>
    <article><div><span>FUNCTIONS</span><h3>函数保持函数。</h3><p>顶层函数、Lambda、函数值、声明引用和 Extension 共用静态类型与普通调用规则。</p></div><pre v-pre><code>extension T echoed&lt;T&gt;(T value) {&#10;  return value&#10;}&#10;&#10;String copy = "Norm".echoed()</code></pre></article>
  </div>
</section>

<section class="norm-section">
  <p class="norm-kicker">高级语言能力</p>
  <h2>编译期知道的信息，不在运行时悄悄消失。</h2>
  <div class="norm-path-grid">
    <article><span>REIFIED GENERICS</span><h3>泛型保留精确类型</h3><p>实际类型参数进入 Canonical Core 和运行时类型环境，反射、Annotation 与序列化面对同一份类型事实。</p></article>
    <article><span>REFERENCES</span><h3>受控的可变访问</h3><p><code>&amp;</code> 取得位置，<code>*</code> 读写 value；引用不能逃逸到返回值、字段、容器或 Lambda。</p></article>
    <article><span>ANNOTATIONS</span><h3>类型化元数据与行为</h3><p>目标、保留策略、参数和拦截生命周期由类型系统检查，并与被拦截字段和参数精确匹配。</p></article>
  </div>
  <a class="norm-section-link" href="./guide/design-whitepaper">深入了解语言能力 →</a>
</section>

<section class="norm-core-section">
  <div class="norm-section">
    <p class="norm-kicker">受 Unison 启发的代码模型</p>
    <h2>源码给人，语义身份给编译器。</h2>
    <p class="norm-section__lead">Unison 的关键启发，是把人类操作代码的界面与编译器识别代码的身份分开。Norm 沿用这条分层：普通 <code>.norm</code> 文件是编写与版本管理界面，content-addressed semantic definitions 是编译器内部的真实代码身份与依赖模型。</p>
    <div class="norm-core-model">
      <article>
        <span>AUTHORING SOURCE</span>
        <h3>普通 <code>.norm</code> 文件</h3>
        <p>开发者继续使用熟悉的编辑器、文本 Diff、Code Review 和 Git。名称、源码位置与排版服务于阅读和协作。</p>
        <b>源码 · 名称 · Git</b>
      </article>
      <div class="norm-core-model__arrow" aria-hidden="true">→</div>
      <article>
        <span>SEMANTIC DEFINITIONS</span>
        <h3>Content-addressed identity</h3>
        <p>解析和类型检查之后，定义身份由规范化语义内容与真实依赖决定，不依赖文件路径或声明顺序。</p>
        <b>Canonical Core · Definition ID</b>
      </article>
    </div>
    <p class="norm-core-result">稳定的语义身份连接精确增量失效、跨进程 Definition Store 和 Truffle Artifact 复用；authoring metadata 仍把诊断、导航与调用栈映射回源码。</p>
    <a class="norm-section-link" href="./spec/compiler-design">查看编译器架构 →</a>
  </div>
</section>

<section class="norm-blue-band">
  <div class="norm-section norm-blue-band__inner">
    <div><p class="norm-kicker">一套语义模型</p><h2>编译器、工具与执行共享同一事实。</h2></div>
    <p>Formatter、补全、签名、Hover、导航和 Rename 读取编译器语义快照；Canonical Core 固定解析结果，Truffle 只执行已解析表示。</p>
    <a class="norm-blue-band__link" href="./tooling/">了解工具链 →</a>
  </div>
</section>
