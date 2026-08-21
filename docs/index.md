---
layout: home
title: Norm：显式共享与值流的静态类型语言
titleTemplate: false
pageClass: norm-home
---

<section class="norm-hero">
  <div class="norm-hero__inner">
    <p class="norm-hero__eyebrow">静态类型编程语言</p>
    <h1>共享、值流、类型信息，全都写在代码里。</h1>
    <p class="norm-hero__lead">Norm 让 class 默认拥有值语义，用 <code>Ref&lt;T&gt;</code> 显式引入共享，以 <code>break value</code> 让控制流产值，并在运行时保留完整泛型参数。</p>
    <div class="norm-hero__actions">
      <a class="norm-button norm-button--dark" href="./language/overview">阅读语言手册</a>
      <a class="norm-button norm-button--light" href="./spec/language-spec">查看语言规范</a>
    </div>
    <div class="norm-code-window" aria-label="Norm 值语义示例">
      <div class="norm-code-window__bar"><span></span><span></span><span></span><b>counter.norm</b></div>
      <pre><code v-pre>class Counter {&#10;    int value&#10;}&#10;&#10;Counter first = Counter(value = 0)&#10;Counter second = first&#10;second.value = 1&#10;&#10;// first.value 仍然是 0&#10;Ref&lt;Counter&gt; shared = first.ref()</code></pre>
    </div>
  </div>
</section>

<section class="norm-intro norm-section">
  <p class="norm-kicker">核心语法</p>
  <h2>三项构成 Norm 的设计</h2>
  <p class="norm-section__lead">它们不是框架约定，而是编译器必须实现、所有 Norm 程序共同遵守的语言规则。</p>
  <div class="norm-feature-grid">
    <article><span class="norm-feature-number">01</span><h3>值语义与 <code>Ref&lt;T&gt;</code></h3><p>class 赋值默认产生独立值。共享 identity 只有通过 <code>.ref()</code> 才会出现。</p></article>
    <article><span class="norm-feature-number">02</span><h3><code>break value</code></h3><p><code>if</code>、<code>for</code> 和 <code>switch</code> 可以产值，但每条路径必须明确写出结果。</p></article>
    <article><span class="norm-feature-number">03</span><h3>运行时泛型</h3><p><code>List&lt;String&gt;.class</code> 保留实际类型参数，不依赖类型擦除或额外 type token。</p></article>
  </div>
</section>

<section class="norm-dark-section">
  <div class="norm-section norm-split">
    <div>
      <p class="norm-kicker">值语义与共享</p>
      <h2>一次赋值，不该悄悄连接两个可变对象。</h2>
      <p>普通 class 和容器在语言语义上递归复制。运行时可以使用写时复制等优化，但共享状态必须在类型中表现为 <code>Ref&lt;T&gt;</code>。</p>
      <a class="norm-text-link" href="./language/objects">了解 Class、Value 与 Ref →</a>
    </div>
    <div class="norm-compare">
      <div><small>独立值</small><pre><code v-pre>Counter second = first&#10;second.value = 1&#10;&#10;// first.value 不变</code></pre></div>
      <div><small>显式共享</small><pre><code v-pre>Ref&lt;Counter&gt; shared = first.ref()&#10;shared.value = 1&#10;&#10;// 共享写在类型中</code></pre></div>
    </div>
  </div>
</section>

<section class="norm-section norm-syntax-section">
  <p class="norm-kicker">值流与类型信息</p>
  <h2>特殊行为有自己的语法。</h2>
  <div class="norm-showcase-grid">
    <article>
      <div><span>控制流表达式</span><h3>结果从哪里产生，一眼可见。</h3><p>没有隐式最后表达式，也不会为缺失分支自动补 null。</p><a href="./language/control-flow">阅读控制流 →</a></div>
      <pre><code v-pre>String sign = if number &lt; 0 {&#10;    break "negative"&#10;} else {&#10;    break "non-negative"&#10;}</code></pre>
    </article>
    <article>
      <div><span>Reified Generics</span><h3>泛型参数不会在运行时消失。</h3><p>类型检查、反射和通用库可以取得完整的参数化类型。</p><a href="./language/generics">阅读泛型 →</a></div>
      <pre><code v-pre>List&lt;String&gt;.class&#10;List&lt;int&gt;.class&#10;&#10;List&lt;String&gt;.class.T&#10;    == String.class</code></pre>
    </article>
  </div>
</section>

<section class="norm-blue-band">
  <div class="norm-section norm-blue-band__inner">
    <div><p class="norm-kicker">从哪里开始</p><h2>先理解语言，再进入生态。</h2></div>
    <p>手册用于连续学习，规范用于确认边界规则。Norm 当前处于预设计阶段，文档描述的是目标语义，不代表已有可用编译器。</p>
    <div class="norm-chip-list"><a href="./guide/introduction">快速介绍</a><a href="./language/overview">语言手册</a><a href="./spec/language-spec">语言规范</a><a href="./docs/">全部文档</a></div>
  </div>
</section>

<section class="norm-section norm-final-cta">
  <h2>从语言本身开始。</h2>
  <p>用一条连续的阅读路径理解 Norm 的类型、值、函数和控制流。</p>
  <div><a class="norm-button norm-button--blue" href="./language/overview">开始阅读手册</a><a class="norm-button norm-button--outline" href="https://github.com/w0fv1/norm">在 GitHub 上查看</a></div>
</section>

