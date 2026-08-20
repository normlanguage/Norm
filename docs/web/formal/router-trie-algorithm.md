# Router Trie Algorithm

## Data Structure

Router stores paths in a trie.

Node contains:
- static children
- parameter child
- wildcard child
- handler metadata

## Matching

Priority:
1. exact segment
2. parameter segment
3. wildcard segment

Example:

/users/:id

becomes:

users -> parameter -> handler
