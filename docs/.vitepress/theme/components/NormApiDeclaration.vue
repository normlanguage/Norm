<script setup lang="ts">
import type { Declaration, Reference } from '../generated/norm-api'
import NormApiDocument from './NormApiDocument.vue'

defineOptions({ name: 'NormApiDeclaration' })
const emit = defineEmits<{ navigate: [reference: Reference] }>()
defineProps<{
  declaration: Declaration
  targets: ReadonlySet<string>
}>()
</script>

<template>
  <article :id="declaration.id" class="norm-api-declaration">
    <header>
      <span>{{ declaration.kind }}</span>
      <a :href="`#${declaration.id}`" :aria-label="`Link to ${declaration.name}`">#</a>
    </header>
    <pre><code>{{ declaration.signature }}</code></pre>
    <NormApiDocument
      v-if="declaration.document"
      :document="declaration.document"
      :targets="targets"
      @navigate="emit('navigate', $event)"
    />
    <dl v-if="declaration.parameters.length" class="norm-api-parameters">
      <template v-for="parameter in declaration.parameters" :key="parameter.name">
        <dt><code>{{ parameter.name }}: {{ parameter.type.display }}</code></dt>
        <dd v-if="parameter.document">
          <NormApiDocument :document="parameter.document" :targets="targets"
      @navigate="emit('navigate', $event)" />
        </dd>
        <dd v-else />
      </template>
    </dl>
    <section v-if="declaration.members.length" class="norm-api-members">
      <NormApiDeclaration
        v-for="member in declaration.members"
        :key="member.id"
        :declaration="member"
        :targets="targets"
      @navigate="emit('navigate', $event)"
      />
    </section>
  </article>
</template>
