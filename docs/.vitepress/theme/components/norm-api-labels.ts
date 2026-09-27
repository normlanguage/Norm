export type NormApiLabels = Readonly<{
  module: string
  moduleFiles: string
  related: string
  unitTests: string
  loadingApi: string
  loadingModule: string
  empty: string
  test: string
  linkTo: (name: string) => string
}>

const english: NormApiLabels = {
  module: 'Norm module',
  moduleFiles: 'Module files',
  related: 'Related',
  unitTests: 'Unit tests',
  loadingApi: 'Loading API documentation…',
  loadingModule: 'Loading module documentation…',
  empty: 'This file does not export public declarations.',
  test: 'Test',
  linkTo: name => `Link to ${name}`,
}

const chinese: NormApiLabels = {
  module: 'Norm 模块',
  moduleFiles: '模块文件',
  related: '相关声明',
  unitTests: '单元测试',
  loadingApi: '正在加载 API 文档…',
  loadingModule: '正在加载模块文档…',
  empty: '此文件未导出公开声明。',
  test: '测试',
  linkTo: name => `链接至 ${name}`,
}

export function labelsFor(locale: string): NormApiLabels {
  return locale.startsWith('zh') ? chinese : english
}
