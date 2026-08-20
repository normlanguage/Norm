# Norm Object Model Specification

## Overview
Norm distinguishes class, value, interface and Ref.

## Class
Class instances have behavior, inheritance and optional shared identity.

Copying a class value follows recursive value semantics. Runtime may optimize using copy-on-write.

## Value
Value types represent pure data. They cannot be modified in place.

## Interface
Interface defines behavior only. It has no fields.

## Ref
Ref&lt;T&gt; explicitly introduces shared mutable identity. It is visible in source code.

## Inheritance
Norm uses single class inheritance and multiple interface implementation.

Public methods are virtual by default. Private methods belong only to their declaring class.
