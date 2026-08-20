# Cranelift Instruction Mapping

Norm IR instructions map to Cranelift IR.

| Norm IR | Cranelift |
|---|---|
| add | iadd |
| sub | isub |
| mul | imul |
| compare | icmp |
| branch | brz/brnz |
| call | call |
| return | return |

Cranelift is used for fast compilation and development mode.
