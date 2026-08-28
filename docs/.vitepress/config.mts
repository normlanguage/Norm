import { defineConfig } from 'vitepress'
import { siteBase, sitePath, siteUrl } from '../scripts/site-config.mjs'
import { zhTheme } from './locales/zh'
import { enTheme } from './locales/en'

export default defineConfig({
  title: 'Norm',
  base: siteBase,
  cleanUrls: true,
  lastUpdated: true,
  markdown: { languageAlias: { norm: 'java' } },
  head: [
    ['meta', { name: 'theme-color', content: '#3178c6' }],
    ['link', { rel: 'icon', type: 'image/svg+xml', href: sitePath('brand/norm.svg') }],
    ['link', { rel: 'alternate icon', href: sitePath('brand/norm.ico') }],
    ['link', { rel: 'apple-touch-icon', sizes: '180x180', href: sitePath('brand/norm-180.png') }],
    ['link', { rel: 'manifest', href: sitePath('site.webmanifest') }],
    ['meta', { property: 'og:site_name', content: 'Norm' }],
    ['meta', { property: 'og:image', content: `${siteUrl}brand/norm-512.png` }],
    ['meta', { name: 'twitter:card', content: 'summary' }],
    ['meta', { name: 'twitter:image', content: `${siteUrl}brand/norm-512.png` }],
  ],
  locales: {
    root: {
      label: '简体中文',
      lang: 'zh-CN',
      title: 'Norm',
      description: '用不同语言构造表达身份、值、选择、能力与受控别名',
      themeConfig: zhTheme,
    },
    en: {
      label: 'English',
      lang: 'en-US',
      link: '/en/',
      title: 'Norm',
      description: 'A statically typed language with familiar syntax and explicit semantics',
      themeConfig: enTheme,
    },
  },
  themeConfig: {
    logo: '/brand/norm.svg',
    siteTitle: 'Norm',
    socialLinks: [{ icon: 'github', link: 'https://github.com/w0fv1/norm' }],
    search: {
      provider: 'local',
      options: {
        locales: {
          root: {
            translations: {
              button: { buttonText: '搜索', buttonAriaLabel: '搜索文档' },
              modal: {
                displayDetails: '显示详细列表',
                resetButtonTitle: '重置搜索',
                backButtonTitle: '关闭搜索',
                noResultsText: '没有找到相关结果',
                footer: {
                  selectText: '选择',
                  selectKeyAriaLabel: '回车',
                  navigateText: '导航',
                  navigateUpKeyAriaLabel: '向上',
                  navigateDownKeyAriaLabel: '向下',
                  closeText: '关闭',
                  closeKeyAriaLabel: 'Esc',
                },
              },
            },
          },
        },
      },
    },
    // Not every deep reference page is translated yet. Until it is, the
    // locale menu lands on the target homepage instead of producing a 404.
    i18nRouting: false,
  },
})
