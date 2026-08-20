import { defineConfig } from 'vitepress'
export default defineConfig({
  lang:'zh-CN', title:'Norm', description:'一门面向应用层软件的静态强类型编程语言', base:'/norm/', cleanUrls:true, lastUpdated:true,
  themeConfig:{
    siteTitle:'Norm',
    nav:[{text:'哲学',link:'/guide/philosophy'},{text:'语言',link:'/language/overview'},{text:'运行时',link:'/runtime/architecture'},{text:'生态',link:'/ecosystem/strategy'},{text:'路线图',link:'/design/roadmap'}],
    sidebar:[
      {text:'开始',items:[{text:'Norm 是什么',link:'/guide/introduction'},{text:'语言哲学',link:'/guide/philosophy'}]},
      {text:'语言手册',items:[{text:'语法总览',link:'/language/overview'},{text:'类型与 Null',link:'/language/types'},{text:'Class / Value / Ref',link:'/language/objects'},{text:'函数',link:'/language/functions'},{text:'控制流',link:'/language/control-flow'},{text:'Enum / Switch',link:'/language/enum-switch'},{text:'泛型',link:'/language/generics'},{text:'错误处理',link:'/language/errors'},{text:'Annotation / Reflect',link:'/language/reflect'}]},
      {text:'实现',items:[{text:'总体架构',link:'/runtime/architecture'},{text:'GraalVM / Truffle',link:'/runtime/graalvm-truffle'}]},
      {text:'生态',items:[{text:'生态策略',link:'/ecosystem/strategy'},{text:'Web 示例',link:'/examples/web'}]},
      {text:'未来',items:[{text:'技术方案',link:'/design/technical-plan'},{text:'路线图',link:'/design/roadmap'}]}
    ],
    socialLinks:[{icon:'github',link:'https://github.com/w0fv1/norm'}], search:{provider:'local'}, footer:{message:'Norm is currently a language pre-design.',copyright:'Norm Project'}
  }
})
