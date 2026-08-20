# Norm Backend Pipeline

The backend converts Norm IR into executable representations.

Pipeline:

Norm IR -> Lowering -> Target IR -> Machine Code

Supported targets:

- LLVM
- Cranelift
- Truffle runtime

The backend must preserve Norm semantics including value copying, references, exceptions and reflection metadata.
