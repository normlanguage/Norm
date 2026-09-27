import type { DefaultTheme } from 'vitepress'
import { currentRelease } from '../release'
import { navigationFor } from './navigation'

export const zhTheme: DefaultTheme.Config = {
  ...navigationFor('zh'),
  outline: { level: [2, 3], label: '本页内容' },
  docFooter: { prev: '上一页', next: '下一页' },
  lastUpdated: { text: '最后更新于' },
  returnToTopLabel: '返回顶部',
  sidebarMenuLabel: '目录',
  darkModeSwitchLabel: '外观',
  langMenuLabel: '切换语言',
  footer: { message: `Norm ${currentRelease}`, copyright: 'Norm Project' },
}
