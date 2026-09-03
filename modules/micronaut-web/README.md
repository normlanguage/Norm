# Micronaut Web

`micronaut.web@2` 是 Micronaut Web 应用的组合 Module。依赖图以 [`module.norm`](micronaut/web/module.norm) 为唯一声明源，公开的类型化配置与自动生命周期入口见 [`Application.norm`](micronaut/web/Application.norm)。配置树通过 `std.configuration` 自动映射为 Micronaut 属性，端到端验收见 [`micronaut-bbs`](../../docs/examples/micronaut-bbs/README.md)。
