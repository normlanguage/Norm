import MarkdownIt from 'markdown-it'
import type { FileEntry, TreeEntry } from '../generated/norm-api'

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
