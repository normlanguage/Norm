# Norm Language Whitepaper

## 1. Introduction

Norm is a statically typed application-oriented programming language designed for large-scale software development. The purpose of Norm is not to maximize language features, but to provide a predictable and readable programming model for building real applications.

Norm targets backend services, enterprise applications, desktop software, command line tools and general application development.

The core idea is:

> Ordinary code should remain ordinary. The meaning of a program should be visible from the source code.

Norm combines:

- Java-like readability and ecosystem practicality
- Strong static typing
- Explicit null safety
- Value semantics by default
- Explicit shared references
- Modern control-flow expressions
- Reified generics
- Annotation based metadata and reflection
- Native executable deployment
- Interpreter and compiler execution modes

Norm is designed as a language, not as a collection of syntax features.

---

## 2. Why Norm Exists

Modern application development has several conflicting trends.

Java provides excellent engineering discipline, but carries historical design limitations:

- everything being forced into classes
- type erasure generics
- confusing object equality
- excessive framework magic
- verbose property patterns

JavaScript and TypeScript provide flexibility, but introduce different problems:

- weak runtime type guarantees
- increasingly complex type-level programming
- implicit conversions
- difficult large-system reasoning

Rust provides excellent safety, but introduces concepts that are expensive for ordinary application developers:

- ownership
- borrowing
- lifetimes
- complex trait systems

Kotlin improves many Java problems, but its language surface has expanded significantly.

Norm chooses a different balance:

- safer than JavaScript
- simpler than Kotlin
- easier for application developers than Rust
- more predictable than dynamic languages

---

## 3. Design Principles

### 3.1 Explicit over implicit

Norm avoids hidden behavior.

The following are deliberately avoided:

- macros
- operator overloading
- implicit string conversion
- hidden null creation
- automatic Result propagation
- arbitrary closure capture

Powerful features exist, but they require visible syntax.

Examples:

Shared state:

```norm
Ref<User> user = original.ref()
```

Reflection:

```norm
reflect void beforeFunction(...)
```

These keywords communicate that special behavior exists.

---

### 3.2 Application first

Norm optimizes for software that people actually build:

- Web services
- APIs
- Database applications
- Business systems
- Tools

The language does not prioritize:

- kernel programming
- extreme metaprogramming
- hardware control

Therefore Norm chooses garbage collection and runtime support.

The developer should focus on business logic rather than memory lifetime management.

---

# 4. Type System

Norm uses nominal static typing.

A type relationship exists because it is explicitly declared.

Example:

```norm
interface Serializer {
    String serialize()
}

class UserSerializer implements Serializer {
    String serialize() {
        return "user"
    }
}
```

A class does not automatically implement an interface just because it has the same methods.

This improves readability in large systems.

---

## 4.1 Null Safety

Norm uses non-null by default.

```norm
String name = "Alice"
```

`name` can never become null.

Nullable values require explicit syntax:

```norm
String? email = null
```

This prevents the most common application bug category: unexpected null values.

Norm does not provide late initialization.

Instead, the compiler performs definite assignment analysis.

```norm
class User {
    String name

    User(String name) {
        this.name = name
    }
}
```

The compiler proves every construction path initializes `name`.

---

# 5. Object Model

Norm has three main data models:

- class
- value
- Ref<T>

---

## 5.1 class

Class represents objects with behavior.

```norm
class User {
    String name

    String displayName() {
        return name
    }
}
```

Unlike Java, class assignment uses value semantics.

```norm
User a = User(name = "Alice")
User b = a
```

`b` becomes an independent value copy.

The runtime may optimize this internally through copy-on-write.

The semantic rule remains:

> Assignment does not create hidden shared mutable state.

---

## 5.2 Ref<T>

When sharing is required, it must be explicit.

```norm
Ref<User> user = original.ref()
```

Now both references access the same object.

```norm
user.name = "Bob"
```

changes the shared object.

Ref is never nullable.

Invalid:

```norm
Ref<User>?
Ref<User?>
```

The state model stays simple.

---

## 5.3 value

Value is designed for pure data.

```norm
value Money {
    decimal amount
    Currency currency
}
```

Value provides:

- value equality
- hash support
- immutable fields
- copy semantics

Money should behave like a mathematical value, not an identity object.

---

# 6. Functions

Functions are first-class language structures.

They do not need a class container.

```norm
String hello(String name) {
    return "Hello ${name}"
}
```

This avoids utility classes such as:

```text
StringUtils
MathUtils
FileUtils
```

A module organizes functions.

A class models objects.

---

# 7. Control Flow Philosophy

Norm treats important control structures as expressions.

However, expressions must explicitly produce values.

There is no implicit last-expression return.

Values are produced using break.

Example:

```norm
String status = if active {
    break "running"
} else {
    break "stopped"
}
```

This keeps value flow visible.

---

# 8. For Expression

Norm uses for as the main iteration structure.

```norm
for User user : users {
    print(user.name)
}
```

A for expression must explicitly handle all result paths.

```norm
User admin = for User user : users {
    if user.admin {
        break user
    }
} else {
    break defaultUser
}
```

There is no hidden null result.

The programmer decides what happens when nothing is found.

---

# 9. Runtime Architecture

Norm is designed with multiple execution backends.

The first implementation uses:

```text
Norm Source
    ↓
Parser
    ↓
Semantic Analysis
    ↓
Typed IR
    ↓
Truffle Backend
    ↓
GraalVM
```

This provides:

- interpreter mode
- JIT optimization
- native packaging through Native Image

Long term:

```text
Typed IR
   ├── Truffle backend
   └── Native backend
```

The language model is independent from the execution engine.

---

# 10. Ecosystem Strategy

Norm does not rebuild the entire software ecosystem immediately.

The first stage uses compatibility layers.

Architecture:

```text
Norm API
   ↓
Adapter Layer
   ↓
Existing Java Ecosystem
```

Examples:

Database:

```text
Norm SQL
   ↓
JDBC adapter
   ↓
PostgreSQL/MySQL drivers
```

JSON:

```text
Norm JSON
   ↓
Existing serializer implementation
```

Over time these components can become native Norm implementations.

---

# 11. Development Roadmap

## Stage 1: Language Prototype

Implement:

- lexer
- parser
- AST
- type checker
- interpreter

## Stage 2: Runtime

Implement:

- objects
- value semantics
- Ref
- GC integration
- reflection metadata

## Stage 3: Application Platform

Build:

- HTTP
- JSON
- SQL
- testing
- logging

## Stage 4: Native Backend

Create:

- optimized IR
- native compiler
- production deployment model

---

# Conclusion

Norm is designed around one central idea:

A programming language should make correct application design easy.

It does not try to remove every abstraction. It tries to remove unnecessary uncertainty.

A Norm developer should be able to answer:

- Is this value nullable?
- Is this object shared?
- Can this function fail?
- Where does this behavior come from?
- What does this code actually execute?

by reading the program itself.

That is the meaning of Norm.
