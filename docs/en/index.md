---
layout: home
title: Norm — Explicit sharing and visible value flow
titleTemplate: false
pageClass: norm-home
---

<section class="norm-hero">
  <div class="norm-hero__inner">
    <p class="norm-hero__eyebrow">A statically typed programming language</p>
    <h1>Sharing, value flow, and type information stay visible.</h1>
    <p class="norm-hero__lead">Norm gives classes value semantics by default, introduces shared identity through <code>Ref&lt;T&gt;</code>, produces control-flow values with <code>break value</code>, and preserves generic arguments at runtime.</p>
    <div class="norm-hero__actions">
      <a class="norm-button norm-button--dark" href="./language/overview">Read the handbook</a>
      <a class="norm-button norm-button--light" href="./docs/">Browse the docs</a>
    </div>
    <div class="norm-code-window" aria-label="Norm value semantics example">
      <div class="norm-code-window__bar"><span></span><span></span><span></span><b>counter.norm</b></div>
      <pre><code v-pre>class Counter {&#10;    int value&#10;}&#10;&#10;Counter first = Counter(value = 0)&#10;Counter second = first&#10;second.value = 1&#10;&#10;// first.value is still 0&#10;Ref&lt;Counter&gt; shared = first.ref()</code></pre>
    </div>
  </div>
</section>

<section class="norm-intro norm-section">
  <p class="norm-kicker">Core syntax</p>
  <h2>Three designs define Norm</h2>
  <p class="norm-section__lead">These are compiler-enforced language rules, not conventions supplied by an application framework.</p>
  <div class="norm-feature-grid">
    <article><span class="norm-feature-number">01</span><h3>Value semantics and <code>Ref&lt;T&gt;</code></h3><p>Assigning a class creates an independent value. Shared identity appears only through <code>.ref()</code>.</p></article>
    <article><span class="norm-feature-number">02</span><h3><code>break value</code></h3><p><code>if</code>, <code>for</code>, and <code>switch</code> can produce values, but every path states its result explicitly.</p></article>
    <article><span class="norm-feature-number">03</span><h3>Reified generics</h3><p><code>List&lt;String&gt;.class</code> retains its actual type argument without erasure or an extra type token.</p></article>
  </div>
</section>

<section class="norm-dark-section">
  <div class="norm-section norm-split">
    <div>
      <p class="norm-kicker">Values and sharing</p>
      <h2>An assignment should not silently connect mutable objects.</h2>
      <p>Classes and containers recursively copy at the language level. Implementations may optimize, but shared state must appear in the type as <code>Ref&lt;T&gt;</code>.</p>
      <a class="norm-text-link" href="./language/objects">Learn about values and Ref →</a>
    </div>
    <div class="norm-compare">
      <div><small>Independent value</small><pre><code v-pre>Counter second = first&#10;second.value = 1&#10;&#10;// first.value is unchanged</code></pre></div>
      <div><small>Explicit sharing</small><pre><code v-pre>Ref&lt;Counter&gt; shared = first.ref()&#10;shared.value = 1&#10;&#10;// sharing is visible in the type</code></pre></div>
    </div>
  </div>
</section>

<section class="norm-section norm-syntax-section">
  <p class="norm-kicker">Value flow and type information</p>
  <h2>Special behavior gets dedicated syntax.</h2>
  <div class="norm-showcase-grid">
    <article><div><span>Control-flow expressions</span><h3>See exactly where a result comes from.</h3><p>No implicit final expression and no automatic null for a missing branch.</p><a href="./language/control-flow">Read about control flow →</a></div><pre><code v-pre>String sign = if number &lt; 0 {&#10;    break "negative"&#10;} else {&#10;    break "non-negative"&#10;}</code></pre></article>
    <article><div><span>Reified generics</span><h3>Generic arguments survive at runtime.</h3><p>Reflection and generic libraries can inspect the full parameterized type.</p><a href="./language/generics">Read about generics →</a></div><pre><code v-pre>List&lt;String&gt;.class&#10;List&lt;int&gt;.class&#10;&#10;List&lt;String&gt;.class.T&#10;    == String.class</code></pre></article>
  </div>
</section>

<section class="norm-blue-band">
  <div class="norm-section norm-blue-band__inner">
    <div><p class="norm-kicker">Where to begin</p><h2>Learn the language before the platform.</h2></div>
    <p>The handbook is sequential; the reference is for precise rules. Norm is currently a specification draft and does not yet have a usable compiler.</p>
    <div class="norm-chip-list"><a href="./language/overview">Language handbook</a><a href="./docs/">Documentation</a><a href="./status">Project status</a></div>
  </div>
</section>

<section class="norm-section norm-final-cta">
  <h2>Start with the language itself.</h2>
  <p>Follow one path through Norm's values, control flow, and type model.</p>
  <div><a class="norm-button norm-button--blue" href="./language/overview">Read the handbook</a><a class="norm-button norm-button--outline" href="https://github.com/w0fv1/norm">View on GitHub</a></div>
</section>

