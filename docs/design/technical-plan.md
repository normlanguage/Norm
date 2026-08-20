# 技术方案

## 1. Frontend

Lexer、Parser、AST、name resolution、Nominal type system、null safety、overload resolution、generics/variance、definite assignment、switch exhaustiveness。

## 2. Truffle Runtime

实现 `NormLanguage`、Truffle nodes、`NormClass`、`NormValue`、`NormRef`、reified generic metadata、annotation reflect hooks 与 Java foreign boundary。

## 3. 标准库与真实应用

优先 collections/text/time/io/JSON/HTTP/SQL/test/logging，底层可先复用 JDK/JDBC/Maven。

## 4. Native Distribution

用 Native Image 验证 standalone binary、启动时间、RSS 和服务吞吐。

## 5. 独立 Native Backend

只有当语言本身证明价值后再投入 LLVM/Cranelift 或其他 backend。Frontend 与 Typed IR 从第一天保持独立。
