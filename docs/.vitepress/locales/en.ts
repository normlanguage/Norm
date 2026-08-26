import type { DefaultTheme } from 'vitepress'
import { currentRelease, releaseItems } from '../release'

const handbook = [
  { text: 'Handbook Introduction', link: '/en/language/overview' },
  { text: 'Types and Null', link: '/en/language/types' },
  { text: 'Value and Identity', link: '/en/language/objects' },
  { text: 'Control-Flow Expressions', link: '/en/language/control-flow' },
  { text: 'Reified Generics', link: '/en/language/generics' },
]

export const enTheme: DefaultTheme.Config = {
  nav: [
    { text: 'Docs', link: '/en/docs/' },
    { text: 'Handbook', link: '/en/language/overview', activeMatch: '^/en/language/' },
    { text: 'Releases', link: '/en/versions/', activeMatch: '^/en/versions/' },
    { text: 'Project Status', link: '/en/status' },
  ],
  sidebar: {
    '/en/language/': [{ text: 'Core Language', items: handbook }],
    '/en/versions/': [{ text: 'Releases', items: [
      { text: 'Version index', link: '/en/versions/' },
      ...releaseItems('/en/versions'),
    ]}],
    '/en/design/': [{ text: 'Implementation', items: [
      ...releaseItems('/en/versions'),
      { text: 'Implementation Strategy', link: '/en/design/implementation-strategy' },
      { text: 'Toolchain Development Standard', link: '/en/design/toolchain-development' },
      { text: 'Compiler Bootstrap Plan', link: '/en/design/bootstrap-plan' },
      { text: 'Release Process', link: '/en/design/release-process' },
    ]}],
  },
  outline: { level: [2, 3], label: 'On this page' },
  docFooter: { prev: 'Previous page', next: 'Next page' },
  lastUpdated: { text: 'Last updated' },
  returnToTopLabel: 'Return to top',
  sidebarMenuLabel: 'Menu',
  darkModeSwitchLabel: 'Appearance',
  langMenuLabel: 'Change language',
  footer: { message: `Norm ${currentRelease} development line`, copyright: 'Norm Project' },
}

