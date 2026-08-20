# Norm Declaration Grammar

## Overview

This chapter defines declarations in Norm.

Norm uses type-first declarations. The purpose is to make program structure visible before behavior.

Examples:

```norm
class User {
    String name
}

String format(User user) {
    return user.name
}
```

## Declaration Categories

Norm declarations include:

- package
- import
- class
- value
- interface
- enum
- annotation
- function

## Class Declaration

```norm
class User {
    String name
}
```

A class may contain:

- fields
- constructors
- methods
- accessors

## Interface Declaration

Interfaces define behavior only.

```norm
interface Serializer {
    String serialize()
}
```

Interfaces cannot contain instance fields.

## Enum Declaration

Enums are algebraic data types.

```norm
enum State {
    Active
    Disabled(String reason)
}
```

## Annotation Declaration

Annotations are metadata types.

```norm
annotation Entity {
    String table = ""
}
```

Annotations may expose reflection behavior explicitly through reflect methods.

