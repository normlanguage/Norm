import type { DefaultTheme } from 'vitepress'
import { currentRelease } from '../release'
import { navigationFor } from './navigation'

export const enTheme: DefaultTheme.Config = {
  ...navigationFor('en'),
  outline: { level: [2, 3], label: 'On this page' },
  docFooter: { prev: 'Previous page', next: 'Next page' },
  lastUpdated: { text: 'Last updated' },
  returnToTopLabel: 'Return to top',
  sidebarMenuLabel: 'Menu',
  darkModeSwitchLabel: 'Appearance',
  langMenuLabel: 'Change language',
  footer: { message: `Norm ${currentRelease}`, copyright: 'Norm Project' },
}
