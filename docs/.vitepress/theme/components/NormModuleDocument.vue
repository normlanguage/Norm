<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { withBase } from 'vitepress'
import type { FileApi, FileEntry, ModuleApi } from '../generated/norm-api'
import NormApiDeclaration from './NormApiDeclaration.vue'
import NormApiTree from './NormApiTree.vue'
import { firstFile, renderDescription } from './norm-api-view'

const props = defineProps<{
  root: string
}>()
const manifest = ref<ModuleApi>()
const file = ref<FileApi>()
const selected = ref<FileEntry>()
const loading = ref(true)
const error = ref('')
let mounted = false
let revision = 0

onMounted(async () => {
  mounted = true
  await loadModule()
})

watch(
  () => props.root,
  async () => {
    if (mounted) await loadModule()
  },
)

async function loadModule() {
  const current = ++revision
  loading.value = true
  error.value = ''
  manifest.value = undefined
  file.value = undefined
  selected.value = undefined
  try {
    const value = await fetchJson<ModuleApi>(`${rootUrl()}module.api.json`)
    if (current !== revision) return
    manifest.value = value
    const initial = firstFile(value.tree)
    if (initial) await select(initial, current)
  } catch (failure) {
    if (current === revision) error.value = message(failure)
  } finally {
    if (current === revision) loading.value = false
  }
}

async function select(entry: FileEntry, current = revision) {
  selected.value = entry
  file.value = undefined
  loading.value = true
  error.value = ''
  try {
    const value = await fetchJson<FileApi>(`${rootUrl()}${entry.document}`)
    if (current === revision && selected.value?.document === entry.document) file.value = value
  } catch (failure) {
    if (current === revision) error.value = message(failure)
  } finally {
    if (current === revision) loading.value = false
  }
}

function rootUrl() {
  const root = props.root.endsWith('/') ? props.root : `${props.root}/`
  return /^https?:\/\//.test(root) ? root : withBase(root)
}

async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url)
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`)
  return await response.json() as T
}

function message(failure: unknown) {
  return failure instanceof Error ? failure.message : String(failure)
}
</script>

<template>
  <div class="norm-module-document">
    <header v-if="manifest" class="norm-module-document__header">
      <div>
        <span>Norm module</span>
        <h2>{{ manifest.module.name }}</h2>
      </div>
      <code>v{{ manifest.module.version }}</code>
    </header>
    <div v-if="manifest" class="norm-module-document__body">
      <aside aria-label="Module files">
        <NormApiTree
          :entries="manifest.tree"
          :selected="selected?.document"
          @select="select"
        />
      </aside>
      <main>
        <p v-if="loading" class="norm-api-state">Loading API documentation…</p>
        <p v-else-if="error" class="norm-api-state norm-api-state--error">{{ error }}</p>
        <template v-else-if="file">
          <header class="norm-api-file-header">
            <span>{{ file.package }}</span>
            <h2>{{ file.source.path }}</h2>
            <div
              v-if="file.document"
              class="norm-api-description"
              v-html="renderDescription(file.document.description)"
            />
          </header>
          <NormApiDeclaration
            v-for="declaration in file.declarations"
            :key="declaration.id"
            :declaration="declaration"
          />
          <p v-if="!file.declarations.length" class="norm-api-state">
            This file does not export public declarations.
          </p>
        </template>
      </main>
    </div>
    <p v-else-if="loading" class="norm-api-state">Loading module documentation…</p>
    <p v-else class="norm-api-state norm-api-state--error">{{ error }}</p>
  </div>
</template>

<style scoped>
.norm-module-document { margin: 32px 0 64px; border: 1px solid var(--vp-c-divider); background: var(--vp-c-bg); }
.norm-module-document__header { display: flex; align-items: end; justify-content: space-between; padding: 28px 32px; border-bottom: 1px solid var(--vp-c-divider); }
.norm-module-document__header span, .norm-api-file-header > span { color: var(--vp-c-brand-1); font-size: 12px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
.norm-module-document__header h2, .norm-api-file-header h2 { margin: 4px 0 0; border: 0; }
.norm-module-document__header code { margin-bottom: 6px; }
.norm-module-document__body { display: grid; grid-template-columns: minmax(210px, 270px) minmax(0, 1fr); min-height: 520px; }
aside { padding: 20px 14px; border-right: 1px solid var(--vp-c-divider); background: var(--vp-c-bg-soft); }
main { min-width: 0; padding: 32px; }
.norm-api-file-header { margin-bottom: 30px; }
.norm-api-file-header h2 { font-size: 24px; font-family: var(--vp-font-family-mono); overflow-wrap: anywhere; }
.norm-api-description :deep(p) { margin: 10px 0 0; color: var(--vp-c-text-2); }
.norm-api-state { padding: 24px; color: var(--vp-c-text-2); }
.norm-api-state--error { color: var(--vp-c-danger-1); }
@media (max-width: 760px) {
  .norm-module-document__body { grid-template-columns: 1fr; }
  aside { max-height: 300px; overflow: auto; border-right: 0; border-bottom: 1px solid var(--vp-c-divider); }
  main { padding: 24px 18px; }
}
</style>
