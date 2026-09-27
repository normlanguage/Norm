# Visibility

Norm currently defines only `public` and `private` visibility. It has no default package visibility and does not use `protected` to create inheritance-only APIs.

```norm
class Account {
    private Double balance

    Double currentBalance() {
        return balance
    }
}
```

## Defaults

- Top-level types and functions are `public` by default.
- Members of classes, values, and enums are `public` by default.
- Internal fields used during construction should be explicitly `private`.
- Interface members are always part of the public contract and cannot be `private`.

A public declaration's signature cannot expose a private type:

```norm
private value Token { String text }
Token scan()
```

`scan` exposes the private type in its public signature, so compilation fails.

## Overriding

A public instance method can be overridden by a subclass. Private methods do not participate in dynamic dispatch and cannot be overridden. A same-named private method declared in a subclass is a new member.

Norm provides no `internal`, `friend`, package-private, or other implicit visibility.
