<script setup lang="ts">
import type { Declaration, Document } from '../generated/norm-api'
import { renderDescription } from './norm-api-view'

defineOptions({ name: 'NormApiDeclaration' })
defineProps<{
  declaration: Declaration
}>()

function related(document: Document) {
  return [...document.types, ...document.functions, ...document.fields]
}
</script>

<template>
  <article :id="declaration.id" class="norm-api-declaration">
    <header>
      <span>{{ declaration.kind }}</span>
      <a :href="`#${declaration.id}`" :aria-label="`Link to ${declaration.name}`">#</a>
    </header>
    <pre><code>{{ declaration.signature }}</code></pre>
    <div
      v-if="declaration.document"
      class="norm-api-description"
      v-html="renderDescription(declaration.document.description)"
    />
    <dl v-if="declaration.parameters.length" class="norm-api-parameters">
      <template v-for="parameter in declaration.parameters" :key="parameter.name">
        <dt><code>{{ parameter.name }}: {{ parameter.type.display }}</code></dt>
        <dd
          v-if="parameter.document"
          v-html="renderDescription(parameter.document.description)"
        />
        <dd v-else />
      </template>
    </dl>
    <nav
      v-if="declaration.document && related(declaration.document).length"
      class="norm-api-related"
      aria-label="Related declarations"
    >
      <span>Related</span>
      <a
        v-for="reference in related(declaration.document)"
        :key="`${reference.kind}:${reference.target}`"
        :href="`#${reference.target}`"
      >
        {{ reference.display }}
      </a>
    </nav>
    <section v-if="declaration.members.length" class="norm-api-members">
      <NormApiDeclaration
        v-for="member in declaration.members"
        :key="member.id"
        :declaration="member"
      />
    </section>
  </article>
</template>
