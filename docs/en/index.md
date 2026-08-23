---
layout: home
title: Norm — A statically typed language for computation
titleTemplate: false
pageClass: norm-home
---

<section class="norm-hero">
  <div class="norm-hero__inner">
    <p class="norm-hero__eyebrow">A statically typed programming language</p>
    <h1>Clear calls, inferred loops, and explicit object identity.</h1>
    <p class="norm-hero__lead">Norm preserves class identity, gives values structural semantics, makes calls readable with argument labels, and removes redundant annotations through static type inference.</p>
    <div class="norm-hero__actions">
      <a class="norm-button norm-button--dark" href="./language/overview">Read the handbook</a>
      <a class="norm-button norm-button--light" href="./docs/">Browse the docs</a>
    </div>
    <div class="norm-code-window" aria-label="Norm object identity example">
      <div class="norm-code-window__bar"><span></span><span></span><span></span><b>counter.norm</b></div>
      <pre><code v-pre>class Counter {&#10;    Integer value&#10;}&#10;&#10;Counter first = Counter(value: 0)&#10;Counter second = first&#10;second.value = 1&#10;&#10;printLine(first.value) // 1&#10;Counter copied = first.copy()</code></pre>
    </div>
  </div>
</section>

<section class="norm-intro norm-section">
  <p class="norm-kicker">Core syntax</p>
  <h2>Three designs define Norm</h2>
  <p class="norm-section__lead">These are compiler-enforced language rules, not conventions supplied by an application framework.</p>
  <div class="norm-feature-grid">
    <article><span class="norm-feature-number">01</span><h3>Value and identity</h3><p>Class assignment preserves object identity, containers copy their structure, and <code>copy()</code> creates a new object.</p></article>
    <article><span class="norm-feature-number">02</span><h3><code>break value</code></h3><p><code>if</code>, <code>for</code>, and <code>switch</code> can produce values, but every path states its result explicitly.</p></article>
    <article><span class="norm-feature-number">03</span><h3>Reified generics</h3><p><code>List&lt;String&gt;.class</code> retains its actual type argument without erasure or an extra type token.</p></article>
  </div>
</section>

<section class="norm-dark-section">
  <div class="norm-section norm-split">
    <div>
      <p class="norm-kicker">Value and identity</p>
      <h2>Data categories determine assignment behavior.</h2>
      <p>Class variables copy object references, while containers copy their own structure. Class elements inside a container retain their identity.</p>
      <a class="norm-text-link" href="./language/objects">Learn about value and identity →</a>
    </div>
    <div class="norm-compare">
      <div><small>Shared object identity</small><pre><code v-pre>Counter second = first&#10;second.value = 1&#10;&#10;// first.value == 1</code></pre></div>
      <div><small>New identity</small><pre><code v-pre>Counter copied = first.copy()&#10;copied.value = 2&#10;&#10;// first.value == 1</code></pre></div>
    </div>
  </div>
</section>

<section class="norm-section norm-syntax-section">
  <p class="norm-kicker">Value flow and type information</p>
  <h2>Special behavior gets dedicated syntax.</h2>
  <div class="norm-showcase-grid">
    <article><div><span>Control-flow expressions</span><h3>See exactly where a result comes from.</h3><p>No implicit final expression and no automatic null for a missing branch.</p><a href="./language/control-flow">Read about control flow →</a></div><pre><code v-pre>String sign = if number &lt; 0 {&#10;    break "negative"&#10;} else {&#10;    break "non-negative"&#10;}</code></pre></article>
    <article><div><span>Reified generics</span><h3>Generic arguments survive at runtime.</h3><p>Reflection and generic libraries can inspect the full parameterized type.</p><a href="./language/generics">Read about generics →</a></div><pre><code v-pre>List&lt;String&gt;.class&#10;List&lt;Integer&gt;.class&#10;&#10;List&lt;String&gt;.class.T&#10;    == String.class</code></pre></article>
  </div>
</section>

<section class="norm-blue-band">
  <div class="norm-section norm-blue-band__inner">
    <div><p class="norm-kicker">Where to begin</p><h2>Learn the language before the platform.</h2></div>
    <p>The handbook is sequential, the specification defines precise rules, and the standard library reference documents reusable types and APIs.</p>
    <div class="norm-chip-list"><a href="./language/overview">Language handbook</a><a href="./docs/">Documentation</a><a href="./status">Project status</a></div>
  </div>
</section>

<section class="norm-section norm-final-cta">
  <h2>Start with the language itself.</h2>
  <p>Follow one path through Norm's values, control flow, and type model.</p>
  <div><a class="norm-button norm-button--blue" href="./language/overview">Read the handbook</a><a class="norm-button norm-button--outline" href="https://github.com/w0fv1/norm">View on GitHub</a></div>
</section>

