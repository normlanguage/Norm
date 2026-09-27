---
layout: home
title: Norm：熟悉的语法，明确的语义
titleTemplate: false
pageClass: norm-home
---

<script setup>
import { ref } from 'vue'
import simpleCode from '../../norm/tests/docs/showcase/simple.norm?raw'
import simpleOutput from '../../norm/tests/docs/showcase/simple.out?raw'
import agentCode from '../../norm/tests/docs/showcase/agent_document.norm?raw'
import agentOutput from '../../norm/tests/docs/showcase/agent_document.out?raw'
import agentQueryOutput from '../../norm/tests/docs/showcase/agent_document.query.out?raw'
import dataCode from '../../norm/tests/docs/showcase/application_data.norm?raw'
import dataOutput from '../../norm/tests/docs/showcase/application_data.out?raw'
import reactiveCode from '../../norm/tests/docs/showcase/application_reactive.norm?raw'
import reactiveOutput from '../../norm/tests/docs/showcase/application_reactive.out?raw'
import aopCode from '../../norm/tests/docs/showcase/application_aop.norm?raw'
import aopOutput from '../../norm/tests/docs/showcase/application_aop.out?raw'
import genericCode from '../../norm/tests/docs/showcase/application_generics.norm?raw'
import genericOutput from '../../norm/tests/docs/showcase/application_generics.out?raw'

const applicationExamples = [
  { title: '数据与决策', filename: 'application_data.norm', detail: '把身份、值、有限状态、命名调用和表达式结果组合在一起。', code: dataCode, output: dataOutput },
  { title: '响应式字段', filename: 'application_reactive.norm', detail: '观察普通强类型字段和集合变化，并在用完后关闭订阅。', code: reactiveCode, output: reactiveOutput },
  { title: '类型化行为', filename: 'application_aop.norm', detail: '用 Norm 编写拦截器，并保留被拦截函数的返回类型。', code: aopCode, output: aopOutput },
  { title: '精确的泛型', filename: 'application_generics.norm', detail: '即使经过 Any，仍能区分 Box<Integer> 与 Box<String>。', code: genericCode, output: genericOutput },
]
const selectedExample = ref(0)
</script>

<section class="norm-hero">
  <div class="norm-hero__inner">
    <img class="norm-hero__logo" src="/brand/norm.svg" alt="Norm Logo">
    <p class="norm-hero__eyebrow">Simple</p>
    <h1><span>熟悉的语法，</span><span>明确的语义。</span></h1>
    <p class="norm-hero__lead">先读懂、运行、修改一个小程序，再看 Norm 如何表达应用，以及如何帮助 Agent 理解代码。</p>
    <div class="norm-hero__actions"><a class="norm-button norm-button--dark" href="./learn/">开始学习</a><a class="norm-button norm-button--light" href="./spec/language-spec">语言参考</a></div>
    <div class="norm-code-window">
      <div class="norm-code-window__bar"><span></span><span></span><span></span><b>simple.norm</b></div>
      <pre><code>{{ simpleCode }}</code></pre>
    </div>
    <p class="norm-hero__result">运行 <code>norm simple.norm</code> → <code>{{ simpleOutput.trim() }}</code></p>
  </div>
</section>

<section class="norm-section">
  <p class="norm-kicker">Agent first</p>
  <h2>给 Agent 可以查询、可以验证的代码事实。</h2>
  <p class="norm-section__lead"><code>@Document</code> 描述声明意图，用受检查的引用连接相关 API。编译器提供签名与类型，测试关联来自 <code>@Test</code>。</p>
  <div class="norm-sample-layout">
    <div class="norm-code-window norm-sample-code">
      <div class="norm-code-window__bar"><span></span><span></span><span></span><b>agent_document.norm</b></div>
      <pre><code>{{ agentCode }}</code></pre>
    </div>
    <div class="norm-sample-aside">
      <ol class="norm-agent-steps">
        <li><strong>理解</strong><code>norm query agent_document.norm describe --source --references --tests</code><code>norm query agent_document.norm TaskApi --source</code></li>
        <li><strong>预览</strong><code>norm refactor name agent_document.norm describe --to summarize --preview</code></li>
        <li><strong>验证</strong><code>norm check agent_document.norm --format json</code><code>norm test agent_document.norm --format json</code></li>
      </ol>
      <p class="norm-sample-output"><strong>查询结果节选</strong><code>{{ agentQueryOutput.trim() }}</code></p>
      <p class="norm-sample-output"><strong>程序输出</strong><code>{{ agentOutput.trim() }}</code></p>
      <p class="norm-development-note">这些能力已包含在 <a href="./versions/0.25">Norm 0.25</a> 中。Agent 在验证前应用预览中的编辑。</p>
      <a class="norm-section-link" href="./tooling/agent">了解 Agent 工具 →</a>
    </div>
  </div>
</section>

<section class="norm-dark-section">
  <div class="norm-section">
    <p class="norm-kicker">Application development</p>
    <h2>让应用规则直接写在代码里。</h2>
    <p class="norm-section__lead">每个示例都是可独立运行的完整文件，旁边展示经过验收的结果。选择想了解的问题。</p>
    <div class="norm-sample-tabs" aria-label="应用示例">
      <button v-for="(example, index) in applicationExamples" :key="example.title" type="button" :aria-pressed="selectedExample === index" @click="selectedExample = index">{{ example.title }}</button>
    </div>
    <p class="norm-sample-description">{{ applicationExamples[selectedExample].detail }}</p>
    <div class="norm-sample-layout">
      <div class="norm-code-window norm-sample-code">
        <div class="norm-code-window__bar"><span></span><span></span><span></span><b>{{ applicationExamples[selectedExample].filename }}</b></div>
        <pre><code>{{ applicationExamples[selectedExample].code }}</code></pre>
      </div>
      <div class="norm-sample-aside">
        <p class="norm-sample-command">运行 <code>norm {{ applicationExamples[selectedExample].filename }}</code></p>
        <p class="norm-sample-output"><strong>验收输出</strong><code>{{ applicationExamples[selectedExample].output.trim() }}</code></p>
        <p class="norm-development-note">这些示例可用 <a href="./versions/0.25">Norm 0.25</a> 运行。</p>
        <a class="norm-section-link" href="./spec/language-spec">继续了解语言特性 →</a>
      </div>
    </div>
  </div>
</section>

<section class="norm-section norm-final-cta"><h2>从一个特性开始。</h2><p>每节教学都有可执行示例，并由编译器持续验收。</p><div><a class="norm-button norm-button--blue" href="./learn/">按顺序学习</a><a class="norm-button norm-button--outline" href="https://github.com/normlanguage/Norm">查看 GitHub</a></div></section>
