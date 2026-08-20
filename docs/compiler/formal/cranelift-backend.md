# Cranelift Backend Design

Cranelift is a fast compilation backend for development builds and incremental compilation.

## Goals

- fast compilation
- simple lowering
- portable machine code generation

## Backend Flow

Norm IR -> Cranelift IR -> Machine Code

## Usage

Development mode may prefer Cranelift while production may use LLVM optimization.
