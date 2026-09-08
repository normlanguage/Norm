import MarkdownIt from 'markdown-it'
import type { Declaration, FileEntry, TreeEntry } from '../generated/norm-api'

const markdown = new MarkdownIt({ html: false, linkify: true, typographer: true })

export function renderDescription(value: string): string {
  return markdown.render(value)
}

export function firstFile(entries: TreeEntry[]): FileEntry | undefined {
  for (const entry of entries) {
    if (entry.kind === 'file') return entry
    const nested = firstFile(entry.children)
    if (nested) return nested
  }
  return undefined
}

export function declarationTargets(declarations: Declaration[]): ReadonlySet<string> {
  const targets = new Set<string>()
  const pending = [...declarations]
  while (pending.length) {
    const declaration = pending.pop()!
    targets.add(declaration.id)
    pending.push(...declaration.members)
  }
  return targets
}
