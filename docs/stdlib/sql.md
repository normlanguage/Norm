# Norm SQL API

SQL design separates API from drivers.

Architecture:

Norm SQL API

↓

Adapter

↓

Database driver

Initial adapters may use JDBC compatibility.
