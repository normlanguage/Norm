import { createHash } from 'node:crypto';
import { createReadStream, lstatSync, readlinkSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

function inventory(root) {
  const entries = new Map();
  const visit = (directory, prefix) => {
    for (const name of readdirSync(directory).sort()) {
      const path = join(directory, name);
      const relative = prefix ? `${prefix}/${name}` : name;
      const info = lstatSync(path);
      const type = info.isSymbolicLink() ? 'link' : info.isDirectory() ? 'directory' : info.isFile() ? 'file' : null;
      if (!type) throw new Error(`Unsupported runtime entry: ${relative}`);
      entries.set(relative, {
        path,
        type,
        mode: info.mode & 0o7777,
        size: info.size,
        target: type === 'link' ? readlinkSync(path) : null,
      });
      if (type === 'directory') visit(path, relative);
    }
  };
  if (!statSync(root).isDirectory()) throw new Error(`Runtime tree is not a directory: ${root}`);
  visit(root, '');
  return entries;
}

async function hash(path) {
  const digest = createHash('sha256');
  for await (const chunk of createReadStream(path)) digest.update(chunk);
  return digest.digest('hex');
}

export async function compareRuntimeTrees(source, delivered) {
  if (process.platform !== 'win32' && (statSync(delivered).mode & 0o777) !== 0o755) {
    throw new Error('Delivered runtime root must have mode 755');
  }
  const original = inventory(source);
  const actual = inventory(delivered);
  if (original.size !== actual.size) throw new Error(`Runtime entry count differs: ${original.size} != ${actual.size}`);
  let regularFiles = 0;
  let symbolicLinks = 0;
  let bytes = 0;
  for (const [relative, expected] of original) {
    const found = actual.get(relative);
    if (!found) throw new Error(`Missing delivered runtime entry: ${relative}`);
    if (expected.type !== found.type) throw new Error(`${relative} type differs: ${expected.type} != ${found.type}`);
    if (process.platform !== 'win32' && found.type === 'directory' && (found.mode & 0o005) !== 0o005) {
      throw new Error(`${relative} directory must be traversable by a regular user`);
    }
    if (expected.type !== 'link' && expected.mode !== found.mode) {
      throw new Error(`${relative} mode differs: ${expected.mode.toString(8)} != ${found.mode.toString(8)}`);
    }
    if (expected.type === 'link') {
      symbolicLinks++;
      if (expected.target !== found.target) throw new Error(`${relative} target differs: ${expected.target} != ${found.target}`);
    }
    if (expected.type === 'file') {
      regularFiles++;
      bytes += expected.size;
      if (expected.size !== found.size || await hash(expected.path) !== await hash(found.path)) {
        throw new Error(`${relative} SHA-256 differs`);
      }
    }
  }
  return { entries: original.size, regularFiles, symbolicLinks, bytes };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.length !== 4) throw new Error('Usage: verify-runtime-tree.mjs <source runtime> <delivered runtime>');
  console.log(JSON.stringify(await compareRuntimeTrees(process.argv[2], process.argv[3])));
}
