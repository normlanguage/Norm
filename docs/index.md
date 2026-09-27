---
layout: home
title: 'Norm: Familiar syntax, explicit semantics'
titleTemplate: false
pageClass: norm-home
---

<script setup>
import heroExample from '../norm/tests/docs/tour/05_enum_switch.norm?raw'
</script>

<section class="norm-hero">
  <div class="norm-hero__inner">
    <img class="norm-hero__logo" src="/brand/norm.svg" alt="Norm logo">
    <p class="norm-hero__eyebrow">A statically typed application programming language</p>
    <h1><span>Familiar syntax.</span><span>Explicit semantics.</span></h1>
    <p class="norm-hero__lead">Use <code>class</code> for identity, <code>value</code> for data, <code>enum</code> for alternatives, <code>interface</code> for capabilities, and <code>ref</code> for controlled mutable access.</p>
    <div class="norm-hero__actions"><a class="norm-button norm-button--dark" href="./learn/">Start learning</a><a class="norm-button norm-button--light" href="./guide/">Explore Norm</a></div>
    <div class="norm-code-window" aria-label="A compiled and executed data enum example">
      <div class="norm-code-window__bar"><span></span><span></span><span></span><b>delivery.norm · N-42</b></div>
      <pre><code>{{ heroExample }}</code></pre>
    </div>
  </div>
</section>

<section class="norm-intro norm-section">
  <p class="norm-kicker">One type model</p>
  <h2>Not all data should be the same kind of object.</h2>
  <p class="norm-section__lead">Language constructs state whether data has identity, whether it can change, how it composes, and what callers can rely on.</p>
  <div class="norm-feature-grid norm-semantics-grid">
    <article><span class="norm-feature-number">CLASS</span><h3>Identity</h3><p>An entity with identity and mutable state; assignment, arguments, and returns preserve the same object.</p></article>
    <article><span class="norm-feature-number">VALUE</span><h3>Data</h3><p>A value defined by its fields; its fields cannot be reassigned after construction.</p></article>
    <article><span class="norm-feature-number">ENUM</span><h3>Alternatives</h3><p>A finite set of states that can carry data and be destructured by patterns.</p></article>
    <article><span class="norm-feature-number">INTERFACE</span><h3>Capability</h3><p>A nominal capability and substitution contract that does not change the underlying data category.</p></article>
    <article><span class="norm-feature-number">REF</span><h3>Controlled aliasing</h3><p>A reference to a <code>value</code> storage location, constrained by a lexical lifetime.</p></article>
  </div>
</section>

<section class="norm-dark-section">
  <div class="norm-section norm-split">
    <div><p class="norm-kicker">Data enums and switch</p><h2>The compiler checks every possible state.</h2><p><code>switch</code> is an expression. Variants can carry data and patterns can nest recursively; missing and unreachable branches produce diagnostics.</p><a class="norm-text-link" href="./learn/enum-switch">Learn data enums and pattern matching →</a></div>
    <div class="norm-compare">
      <div><small>Produce a result explicitly</small><pre v-pre><code>case Sent(String code) {&#10;  break code&#10;}</code></pre></div>
      <div><small>Cover the remaining state</small><pre v-pre><code>case Failed(_) {&#10;  break "failed"&#10;}</code></pre></div>
      <div><small>No implicit fallthrough</small><pre v-pre><code>Each case executes alone.&#10;The matched value is evaluated once.</code></pre></div>
    </div>
  </div>
</section>

<section class="norm-section norm-syntax-section">
  <p class="norm-kicker">Types serve understanding</p><h2>Omit what context determines; reject what semantics cannot infer.</h2>
  <div class="norm-showcase-grid norm-showcase-grid--three">
    <article><div><span>NULL SAFETY</span><h3>Absence belongs in the type.</h3><p><code>String?</code>, <code>?.</code>, and <code>??</code> express nullable values, safe access, and fallback paths.</p></div><pre v-pre><code>Integer size(String? text) {&#10;  return text?.graphemeSize() ?? 0&#10;}</code></pre></article>
    <article><div><span>INFERENCE</span><h3>Inference uses expected types.</h3><p>Collection literals and generic constructors use assignment targets and arguments. Failure does not fall back to dynamic typing.</p></div><pre v-pre><code>Array&lt;Integer&gt; fixed = [1, 2, 3]&#10;List&lt;Integer&gt; dynamic = [1, 2, 3]&#10;List&lt;Pair&lt;Integer, String&gt;&gt; pairs = List&lt;&gt;()</code></pre></article>
    <article><div><span>FUNCTIONS</span><h3>Functions remain functions.</h3><p>Top-level functions, lambdas, function values, declaration references, and extensions share static types and ordinary call rules.</p></div><pre v-pre><code>extension T echoed&lt;T&gt;(T value) {&#10;  return value&#10;}&#10;&#10;String copy = "Norm".echoed()</code></pre></article>
  </div>
</section>

<section class="norm-section">
  <p class="norm-kicker">Advanced language features</p><h2>Compile-time knowledge does not quietly disappear at runtime.</h2>
  <div class="norm-path-grid">
    <article><span>REIFIED GENERICS</span><h3>Generic types stay precise</h3><p>Actual type arguments enter Canonical Core and the runtime type environment; reflection, annotations, and serialization share the same type facts.</p></article>
    <article><span>REFERENCES</span><h3>Controlled mutable access</h3><p><code>&amp;</code> obtains a location and <code>*</code> reads or writes a value; references cannot escape into returns, fields, containers, or lambdas.</p></article>
    <article><span>ANNOTATIONS</span><h3>Typed metadata and behavior</h3><p>The type system checks targets, retention, parameters, and interceptor lifecycles against the intercepted fields and parameters.</p></article>
  </div>
  <a class="norm-section-link" href="./guide/design-whitepaper">Explore the language design →</a>
</section>

<section class="norm-core-section">
  <div class="norm-section">
    <p class="norm-kicker">A code model inspired by Unison</p><h2>Source for people; semantic identity for the compiler.</h2>
    <p class="norm-section__lead">Unison inspired a separation between the human interface for editing code and the identity used by the compiler. Norm keeps ordinary <code>.norm</code> files for authoring and version control, while content-addressed semantic definitions provide the compiler's code identity and dependency model.</p>
    <div class="norm-core-model">
      <article><span>AUTHORING SOURCE</span><h3>Ordinary <code>.norm</code> files</h3><p>Developers keep their editors, text diffs, code review, and Git. Names, source locations, and formatting support reading and collaboration.</p><b>Source · Names · Git</b></article>
      <div class="norm-core-model__arrow" aria-hidden="true">→</div>
      <article><span>SEMANTIC DEFINITIONS</span><h3>Content-addressed identity</h3><p>After parsing and type checking, normalized semantic content and actual dependencies determine a definition's identity, independently of file paths or declaration order.</p><b>Canonical Core · Definition ID</b></article>
    </div>
    <p class="norm-core-result">Stable semantic identity connects precise incremental invalidation, a cross-process Definition Store, and Truffle artifact reuse. Authoring metadata still maps diagnostics, navigation, and stack traces back to source.</p>
    <a class="norm-section-link" href="./spec/compiler-design">See the compiler architecture →</a>
  </div>
</section>

<section class="norm-blue-band">
  <div class="norm-section norm-blue-band__inner">
    <div><p class="norm-kicker">One semantic model</p><h2>Compiler, tooling, and execution share the same facts.</h2></div>
    <p>Formatting, completion, signatures, hover, navigation, and rename read compiler semantic snapshots. Canonical Core fixes the resolved result; Truffle executes only that resolved representation.</p>
    <a class="norm-blue-band__link" href="./tooling/">Explore the tooling →</a>
  </div>
</section>

<section class="norm-section norm-final-cta"><h2>Start with the language itself.</h2><p>Follow one path through Norm's values, control flow, and type model.</p><div><a class="norm-button norm-button--blue" href="./learn/">Start learning</a><a class="norm-button norm-button--outline" href="https://github.com/normlanguage/Norm">View on GitHub</a></div></section>
