<script setup lang="ts">
import { computed } from 'vue'
import type { Document, Reference } from '../generated/norm-api'
import { renderDescription } from './norm-api-view'

const props = defineProps<{
  document: Document
  targets: ReadonlySet<string>
}>()

const emit = defineEmits<{ navigate: [reference: Reference] }>()

const groups = computed(() =>
  [
    {
      label: 'Related',
      references: [...props.document.types, ...props.document.functions, ...props.document.fields],
    },
    { label: 'Unit tests', references: props.document.unitTests },
  ].filter(group => group.references.length),
)
</script>

<template>
  <div class="norm-api-description" v-html="renderDescription(document.description)" />
  <nav
    v-for="group in groups"
    :key="group.label"
    class="norm-api-related"
    :aria-label="group.label"
  >
    <span>{{ group.label }}</span>
    <template v-for="(reference, index) in group.references" :key="`${reference.target}:${index}`">
      <a
        v-if="targets.has(reference.target) || reference.document"
        :href="`#${reference.target}`"
        @click.prevent="emit('navigate', reference)"
      >
        {{ reference.display }}
      </a>
      <span v-else>{{ reference.display }}</span>
    </template>
  </nav>
</template>
