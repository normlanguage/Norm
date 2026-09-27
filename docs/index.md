---
layout: home
title: 'Norm: Familiar syntax, explicit semantics'
titleTemplate: false
pageClass: norm-home
---

<script setup>
import { ref } from 'vue'
import simpleCode from '../norm/tests/docs/showcase/simple.norm?raw'
import simpleOutput from '../norm/tests/docs/showcase/simple.out?raw'
import agentCode from '../norm/tests/docs/showcase/agent_document.norm?raw'
import agentOutput from '../norm/tests/docs/showcase/agent_document.out?raw'
import agentQueryOutput from '../norm/tests/docs/showcase/agent_document.query.out?raw'
import dataCode from '../norm/tests/docs/showcase/application_data.norm?raw'
import dataOutput from '../norm/tests/docs/showcase/application_data.out?raw'
import reactiveCode from '../norm/tests/docs/showcase/application_reactive.norm?raw'
import reactiveOutput from '../norm/tests/docs/showcase/application_reactive.out?raw'
import aopCode from '../norm/tests/docs/showcase/application_aop.norm?raw'
import aopOutput from '../norm/tests/docs/showcase/application_aop.out?raw'
import genericCode from '../norm/tests/docs/showcase/application_generics.norm?raw'
import genericOutput from '../norm/tests/docs/showcase/application_generics.out?raw'

const applicationExamples = [
  { title: 'Data and decisions', filename: 'application_data.norm', detail: 'Model identity, values, finite states, named calls, and expression results together.', code: dataCode, output: dataOutput },
  { title: 'Responsive fields', filename: 'application_reactive.norm', detail: 'Subscribe to ordinary typed fields and collection changes; close the subscription when done.', code: reactiveCode, output: reactiveOutput },
  { title: 'Typed behavior', filename: 'application_aop.norm', detail: 'Write an interceptor in Norm and keep the intercepted return type.', code: aopCode, output: aopOutput },
  { title: 'Precise generics', filename: 'application_generics.norm', detail: 'Distinguish Box<Integer> from Box<String> even through Any.', code: genericCode, output: genericOutput },
]
const selectedExample = ref(0)
</script>

<section class="norm-hero">
  <div class="norm-hero__inner">
    <img class="norm-hero__logo" src="/brand/norm.svg" alt="Norm logo">
    <p class="norm-hero__eyebrow">Simple</p>
    <h1><span>Familiar syntax.</span><span>Explicit semantics.</span></h1>
    <p class="norm-hero__lead">Start with a small program you can read, run, and change. Then explore how the same language expresses applications and helps agents work with code.</p>
    <div class="norm-hero__actions"><a class="norm-button norm-button--dark" href="./learn/">Start learning</a><a class="norm-button norm-button--light" href="./spec/language-spec">Language reference</a></div>
    <div class="norm-code-window">
      <div class="norm-code-window__bar"><span></span><span></span><span></span><b>simple.norm</b></div>
      <pre><code>{{ simpleCode }}</code></pre>
    </div>
    <p class="norm-hero__result">Run <code>norm simple.norm</code> → <code>{{ simpleOutput.trim() }}</code></p>
  </div>
</section>

<section class="norm-section">
  <p class="norm-kicker">Agent first</p>
  <h2>Give an agent facts it can inspect and verify.</h2>
  <p class="norm-section__lead">Use <code>@Document</code> to describe the intent of a declaration and connect related APIs through checked references. The compiler supplies its signature and types; test associations come from <code>@Test</code>.</p>
  <div class="norm-sample-layout">
    <div class="norm-code-window norm-sample-code">
      <div class="norm-code-window__bar"><span></span><span></span><span></span><b>agent_document.norm</b></div>
      <pre><code>{{ agentCode }}</code></pre>
    </div>
    <div class="norm-sample-aside">
      <ol class="norm-agent-steps">
        <li><strong>Understand</strong><code>norm query agent_document.norm describe --source --references --tests</code><code>norm query agent_document.norm TaskApi --source</code></li>
        <li><strong>Preview</strong><code>norm refactor name agent_document.norm describe --to summarize --preview</code></li>
        <li><strong>Verify</strong><code>norm check agent_document.norm --format json</code><code>norm test agent_document.norm --format json</code></li>
      </ol>
      <p class="norm-sample-output"><strong>Selected query fields</strong><code>{{ agentQueryOutput.trim() }}</code></p>
      <p class="norm-sample-output"><strong>Program output</strong><code>{{ agentOutput.trim() }}</code></p>
      <p class="norm-development-note">Semantic query and refactoring here require the <a href="./design/distribution-source-build">development toolchain</a>. The agent applies previewed edits before verification.</p>
      <a class="norm-section-link" href="./tooling/agent">Explore Agent tools →</a>
    </div>
  </div>
</section>

<section class="norm-dark-section">
  <div class="norm-section">
    <p class="norm-kicker">Application development</p>
    <h2>Express the rules of a real program in its code.</h2>
    <p class="norm-section__lead">Each example is a complete runnable file with a checked result. Select the problem you want to explore.</p>
    <div class="norm-sample-tabs" aria-label="Application examples">
      <button v-for="(example, index) in applicationExamples" :key="example.title" type="button" :aria-pressed="selectedExample === index" @click="selectedExample = index">{{ example.title }}</button>
    </div>
    <p class="norm-sample-description">{{ applicationExamples[selectedExample].detail }}</p>
    <div class="norm-sample-layout">
      <div class="norm-code-window norm-sample-code">
        <div class="norm-code-window__bar"><span></span><span></span><span></span><b>{{ applicationExamples[selectedExample].filename }}</b></div>
        <pre><code>{{ applicationExamples[selectedExample].code }}</code></pre>
      </div>
      <div class="norm-sample-aside">
        <p class="norm-sample-command">Run <code>norm {{ applicationExamples[selectedExample].filename }}</code></p>
        <p class="norm-sample-output"><strong>Checked output</strong><code>{{ applicationExamples[selectedExample].output.trim() }}</code></p>
        <p class="norm-development-note">These examples require the <a href="./design/distribution-source-build">development toolchain</a>.</p>
        <a class="norm-section-link" href="./spec/language-spec">Explore language features →</a>
      </div>
    </div>
  </div>
</section>

<section class="norm-section norm-final-cta"><h2>Start with one feature.</h2><p>Every learning example is executable and checked against the compiler.</p><div><a class="norm-button norm-button--blue" href="./learn/">Follow the learning path</a><a class="norm-button norm-button--outline" href="https://github.com/normlanguage/Norm">View on GitHub</a></div></section>
