import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

if (process.argv.length !== 4) {
  throw new Error('Usage: verify-cli.mjs <norm-cli> <version>');
}

const repository = resolve(import.meta.dirname, '..', '..', '..');
const cli = resolve(process.argv[2]);
const version = process.argv[3];
verify(['--version'], `norm ${version}\n`);
verify(['run', resolve(repository, 'docs', 'examples', 'hello.norm')], 'Hello from Norm\n');

let count = 0;
for (const group of ['base', 'algorithms', 'class']) {
  const directory = resolve(repository, 'norm', 'tests', group);
  const cases = readFileSync(resolve(directory, 'cases.tsv'), 'utf8').trimEnd().split(/\r?\n/);
  for (const row of cases) {
    const separator = row.indexOf('\t');
    if (separator < 0) throw new Error(`Invalid acceptance case: ${row}`);
    const file = row.slice(0, separator);
    const expected = row.slice(separator + 1).replaceAll('\\n', '\n');
    verify(['run', resolve(directory, file)], expected);
    count += 1;
  }
}

if (count !== 65) throw new Error(`Expected 65 acceptance programs, received ${count}`);
console.log(`Norm CLI verified with ${count} acceptance programs.`);

function verify(args, expected) {
  const commandScript = process.platform === 'win32' && /\.(?:bat|cmd)$/i.test(cli);
  const result = spawnSync(
    commandScript ? (process.env.ComSpec ?? 'cmd.exe') : cli,
    commandScript ? ['/d', '/c', 'call', cli, ...args] : args,
    { cwd: repository, encoding: 'utf8' },
  );
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(`${args.join(' ')} exited with ${result.status}: ${result.stderr}`);
  }
  if (result.stderr) {
    throw new Error(`${args.join(' ')} wrote to stderr: ${result.stderr}`);
  }
  const actual = result.stdout.replaceAll('\r\n', '\n');
  if (actual !== expected) {
    throw new Error(
      `${args.join(' ')} output mismatch\nexpected: ${JSON.stringify(expected)}\nreceived: ${JSON.stringify(actual)}`,
    );
  }
}
