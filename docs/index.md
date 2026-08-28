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
      <a class="norm-button norm-button--light" href="./guide/">了解语言设计</a>
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
    <article><div><span>NULL SAFETY</span><h3>缺失进入类型。</h3><p><code>String?</code>、<code>?.</code> 和 <code>??</code> 分别表达可空值、安全访问和回退路径。</p><a href="./learn/nullability-inference">学习 Null 与推断 →</a></div><pre v-pre><code>Integer size(String? text) {&#10;  return text?.graphemeSize() ?? 0&#10;}</code></pre></article>
    <article><div><span>INFERENCE</span><h3>推断使用期望类型。</h3><p>集合字面量和泛型构造器利用赋值目标与实参；失败时不会退化成动态类型。</p><a href="./spec/type-inference">查看推断规则 →</a></div><pre v-pre><code>Array&lt;Integer&gt; fixed = [1, 2, 3]&#10;List&lt;Integer&gt; dynamic = [1, 2, 3]&#10;List&lt;Pair&lt;Integer, String&gt;&gt; pairs = List&lt;&gt;()</code></pre></article>
    <article><div><span>FUNCTIONS</span><h3>函数保持函数。</h3><p>顶层函数、Lambda、方法引用和 Extension 共用静态类型与普通调用规则。</p><a href="./learn/lambdas-extensions">学习函数值与 Extension →</a></div><pre v-pre><code>extension T echoed&lt;T&gt;(T value) {&#10;  return value&#10;}&#10;&#10;String copy = "Norm".echoed()</code></pre></article>
  </div>
</section>

<section class="norm-section">
  <p class="norm-kicker">高级能力</p>
  <h2>别名和运行时策略仍然有清楚边界。</h2>
  <div class="norm-path-grid">
    <a href="./learn/references"><span>REFERENCES</span><h3>受控的可变访问</h3><p><code>&amp;</code> 取得位置，<code>*</code> 读写 value；引用不能逃逸到返回值、字段、容器或 Lambda。</p><b>学习引用 →</b></a>
    <a href="./learn/annotations"><span>ANNOTATIONS</span><h3>类型化元数据与行为</h3><p>目标、保留策略和拦截生命周期通过名义接口表达，并与字段类型精确匹配。</p><b>学习 Annotation →</b></a>
    <a href="./stdlib/overview"><span>STANDARD LIBRARY</span><h3>Unicode-aware at the core</h3><p>文本 API 区分 byte、code point 和 grapheme，并连接集合、文件、HTTP 与结构数据格式。</p><b>查看标准库 →</b></a>
  </div>
</section>

<section class="norm-blue-band">
  <div class="norm-section norm-blue-band__inner">
    <div><p class="norm-kicker">一套语义模型</p><h2>编译器、工具与执行共享同一事实。</h2></div>
    <p>Formatter、补全、签名、Hover、导航和 Rename 读取编译器语义快照；Canonical Core 固定解析结果，Truffle 只执行已解析表示。</p>
    <div class="norm-chip-list"><a href="./tooling/">Tooling</a><a href="./design/">Compiler Design</a><a href="./status">Current Status</a><a href="./versions/0.16">Norm 0.16</a></div>
  </div>
</section>

<section class="norm-section norm-final-cta">
  <h2>从一段真实的 Norm 程序开始。</h2>
  <p>Tour 中的主要示例由当前编译器编译、执行并校验输出。</p>
  <div><a class="norm-button norm-button--blue" href="./learn/">开始 Language Tour</a><a class="norm-button norm-button--outline" href="https://github.com/w0fv1/Norm/releases/latest">获取最新 Release</a></div>
</section>
