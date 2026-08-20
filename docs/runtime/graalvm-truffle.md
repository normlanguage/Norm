# GraalVM 与 Truffle

Truffle 是 Norm 第一代语言执行后端的候选框架。Norm Typed AST/IR 映射为 Truffle Nodes，由 Graal 对热点路径做 partial evaluation 和 JIT 优化。

优势：不必早期自研 JIT/GC；可利用成熟 host interop 与工具基础；团队可优先投入语言语义和生态。

发布阶段可用 GraalVM Native Image 将 runtime 与应用打包为目标平台原生 executable。长期仍保留独立 native backend 的可能。

Norm 的语言模型不等同于 JVM 模型。
