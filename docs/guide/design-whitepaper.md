# Norm Language Design Whitepaper

## Overview

Norm is designed as an application-oriented programming language. The goal is not to maximize language features, but to provide a stable engineering model where developers can understand code locally and predict its behavior.

Norm combines the strengths of Java's ecosystem and readability, modern null safety, value-oriented data modeling, explicit metaprogramming, and native deployment goals.

This document expands the original design notes into a long-term language specification direction.

## Philosophy

### Explicit behavior

Norm avoids hidden behavior. A programmer reading code should understand important runtime effects.

Examples:

- Shared mutable state requires Ref<T>.
- Reflection requires explicit reflect declarations.
- Business failures use Result<T,E>.
- Unexpected failures use exceptions.
- String formatting uses templates instead of overloaded operators.

### Application first

Norm is designed for:

- backend services
- enterprise systems
- desktop applications
- CLI tools
- business software

The language prioritizes maintainability, ecosystem integration, and predictable execution.

## Object Model

Norm separates three concepts:

### class

Class represents behavior-oriented objects.

A class has:

- fields
- methods
- inheritance
- interface implementation

Class assignment has value semantics by default.

### value

Value represents pure data.

Examples:

- Money
- Date
- Coordinates
- DTO objects

Values cannot be modified in place. A new value is created instead.

### Ref<T>

Ref explicitly introduces shared identity.

This is the only normal way to express shared mutable state.

## Runtime Strategy

The first runtime implementation uses GraalVM and Truffle.

Architecture:

Norm Source

→ Parser

→ Semantic Analyzer

→ Typed IR

→ Truffle Backend

→ GraalVM Runtime

Later, a native backend can compile the same IR directly to machine code.

## Ecosystem Strategy

Norm does not recreate the entire software ecosystem initially.

The first phase uses adapters:

Norm API

→ Compatibility Layer

→ Java ecosystem

Examples:

- JDBC for database drivers
- java.time for time handling
- Java HTTP libraries
- existing JSON libraries

As Norm matures, components can gradually become native implementations.

## Web Development Vision

Norm web applications should avoid excessive framework magic.

A typical application should have:

- explicit routing
- typed request and response objects
- Result based business flow
- annotation based registration
- predictable dependency construction

The language should make common backend code simple without hiding execution.

## Future Direction

Long term goals:

1. Complete compiler frontend
2. Stable type system
3. Standard library
4. Web ecosystem
5. Native compiler backend
6. Debugger and tooling
7. Package ecosystem

Norm should grow through stable foundations rather than continuous addition of syntax features.
