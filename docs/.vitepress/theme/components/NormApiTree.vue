<script setup lang="ts">
import type { FileEntry, TreeEntry } from '../generated/norm-api'

defineOptions({ name: 'NormApiTree' })
defineProps<{
  entries: TreeEntry[]
  selected?: string
}>()
defineEmits<{
  select: [entry: FileEntry]
}>()
</script>

<template>
  <ul class="norm-api-tree">
    <li v-for="entry in entries" :key="entry.kind === 'file' ? entry.document : entry.name">
      <details v-if="entry.kind === 'directory'" open>
        <summary>{{ entry.name }}</summary>
        <NormApiTree
          :entries="entry.children"
          :selected="selected"
          @select="$emit('select', $event)"
        />
      </details>
      <button
        v-else
        type="button"
        :class="{ 'is-selected': selected === entry.document }"
        @click="$emit('select', entry)"
      >
        <span>{{ entry.name }}</span>
        <small>.norm</small>
      </button>
    </li>
  </ul>
</template>
