export const repositoryName = 'Norm'
export const siteOrigin = 'https://normlanguage.github.io'
export const siteBase = `/${repositoryName}/`
export const siteUrl = `${siteOrigin}${siteBase}`
export const sitePath = (path) => `${siteBase}${path.replace(/^\/+/, '')}`
export const siteManifest = {
  name: 'Norm',
  short_name: 'Norm',
  description: 'A statically typed language with familiar syntax and explicit semantics',
  start_url: siteBase,
  scope: siteBase,
  display: 'standalone',
  background_color: '#ffffff',
  theme_color: '#3178c6',
  icons: [
    { src: sitePath('brand/norm-192.png'), sizes: '192x192', type: 'image/png' },
    { src: sitePath('brand/norm-512.png'), sizes: '512x512', type: 'image/png' },
  ],
}
