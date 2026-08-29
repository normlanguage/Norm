import DefaultTheme from 'vitepress/theme'
import './custom.css'
import NormModuleDocument from './components/NormModuleDocument.vue'

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component('NormModuleDocument', NormModuleDocument)
  },
}
