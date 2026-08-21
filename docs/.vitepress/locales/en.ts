import type { DefaultTheme } from 'vitepress'

const handbook = [
  { text: 'Handbook Introduction', link: '/en/language/overview' },
  { text: 'Value Semantics and Ref', link: '/en/language/objects' },
  { text: 'Control-Flow Expressions', link: '/en/language/control-flow' },
  { text: 'Reified Generics', link: '/en/language/generics' },
]

export const enTheme: DefaultTheme.Config = {
  nav: [
    { text: 'Docs', link: '/en/docs/' },
    { text: 'Handbook', link: '/en/language/overview', activeMatch: '^/en/language/' },
    { text: 'Project Status', link: '/en/status' },
  ],
  sidebar: {
    '/en/language/': [{ text: 'Core Language', items: handbook }],
  },
  outline: { level: [2, 3], label: 'On this page' },
  docFooter: { prev: 'Previous page', next: 'Next page' },
  lastUpdated: { text: 'Last updated' },
  returnToTopLabel: 'Return to top',
  sidebarMenuLabel: 'Menu',
  darkModeSwitchLabel: 'Appearance',
  langMenuLabel: 'Change language',
  footer: { message: 'Norm is currently a language specification draft.', copyright: 'Norm Project' },
}

