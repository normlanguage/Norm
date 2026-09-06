import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, rmSync, cpSync, copyFileSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { readNativeSizeSet, compareNativeSizeSets } from './compare-native-size.mjs';

const files = ['size.json', 'build-output.json', 'build-inputs.json', 'java-artifacts.json',
  'execution-verification.json', 'web-verification.json'];

function readSnapshot(directory, identity) {
  const manifest = JSON.parse(readFileSync(resolve(directory, 'manifest.json'), 'utf8'));
  assert.equal(manifest.schemaVersion, 1, 'Invalid baseline manifest');
  for (const key of ['repository', 'workflowId', 'platform'])
    assert.equal(manifest[key], identity[key], `Baseline ${key} mismatch`);
  assert.ok(Number.isSafeInteger(manifest.runId) && manifest.runId > 0, 'Invalid baseline run');
  assert.match(manifest.sourceSha, /^[a-f0-9]{40}$/, 'Invalid baseline source');
  assert.ok(typeof manifest.branch === 'string' && manifest.branch.length > 0, 'Missing source branch');
  assert.ok(typeof manifest.reason === 'string' && manifest.reason.trim().length > 0, 'Missing baseline reason');
  assert.ok(manifest.parent === null || /^[a-f0-9]{40}$/.test(manifest.parent), 'Invalid baseline parent');
  const reports = resolve(directory, 'reports');
  compareNativeSizeSets(reports, reports);
  return { manifest, reports };
}

export function prepareBaseline(candidate, destination, provenance) {
  compareNativeSizeSets(candidate, candidate);
  const samples = readNativeSizeSet(candidate);
  mkdirSync(destination, { recursive: true });
  mkdirSync(resolve(destination, 'reports'));
  for (const sample of samples) {
    assert.match(sample.directory, /^[\w.-]+$/, 'Invalid sample directory');
    const output = resolve(destination, 'reports', sample.directory);
    mkdirSync(output);
    for (const file of files) {
      const input = resolve(candidate, sample.directory, file);
      if (existsSync(input)) copyFileSync(input, resolve(output, file));
    }
  }
  writeFileSync(resolve(destination, 'manifest.json'), JSON.stringify({ schemaVersion: 1, ...provenance }, null, 2) + '\n');
  return readSnapshot(destination, provenance);
}

export class BaselineStore {
  constructor(remote, identity) {
    assert.match(identity.repository, /^[\w.-]+\/[\w.-]+$/);
    assert.match(identity.platform, /^[\w-]+$/);
    assert.ok(Number.isSafeInteger(identity.workflowId) && identity.workflowId > 0);
    this.identity = identity;
    this.ref = `refs/heads/ci/native-size/${identity.workflowId}/${identity.platform}`;
    this.directory = mkdtempSync(resolve(tmpdir(), 'norm-native-baseline-'));
    this.loaded = false;
    this.head = null;
    try {
      this.git(['init', '--quiet']);
      this.git(['remote', 'add', 'origin', remote]);
    } catch (error) {
      this.close();
      throw error;
    }
  }

  git(args, allowed = [0]) {
    const result = spawnSync('git', ['-c', 'credential.helper=', '-c', 'credential.helper=!gh auth git-credential',
      '-c', 'user.name=Norm CI', '-c', 'user.email=ci@normlanguage.invalid', '-c', 'commit.gpgsign=false', ...args],
    { cwd: this.directory, encoding: 'utf8', windowsHide: true, timeout: 30000, maxBuffer: 16 * 1024 * 1024 });
    if (result.error) throw result.error;
    assert.ok(allowed.includes(result.status), `Git baseline operation failed: ${result.stderr}`);
    return { status: result.status, output: result.stdout.trim() };
  }

  load() {
    assert.equal(this.loaded, false, 'Baseline already loaded');
    const found = this.git(['ls-remote', '--exit-code', 'origin', this.ref], [0, 2]);
    this.loaded = true;
    if (found.status === 2) return undefined;
    this.head = found.output.split(/\s+/)[0];
    assert.match(this.head, /^[a-f0-9]{40}$/);
    this.git(['fetch', '--quiet', '--depth=1', 'origin', this.ref]);
    assert.equal(this.git(['rev-parse', 'FETCH_HEAD']).output, this.head, 'Baseline changed during read; retry');
    this.git(['checkout', '--quiet', '--detach', 'FETCH_HEAD']);
    return { ...readSnapshot(this.directory, this.identity), commit: this.head };
  }

  publish(proposal) {
    assert.ok(this.loaded, 'Load baseline before publication');
    const snapshot = readSnapshot(proposal, this.identity);
    assert.equal(snapshot.manifest.parent, this.head, 'Baseline changed; stale proposal');
    const reports = resolve(this.directory, 'reports');
    rmSync(reports, { recursive: true, force: true });
    cpSync(snapshot.reports, reports, { recursive: true });
    copyFileSync(resolve(proposal, 'manifest.json'), resolve(this.directory, 'manifest.json'));
    this.git(['add', '--all', '--', 'manifest.json', 'reports']);
    this.git(['commit', '--quiet', '-m', `Native baseline from run ${snapshot.manifest.runId}`]);
    const commit = this.git(['rev-parse', 'HEAD']).output;
    this.git(['push', `--force-with-lease=${this.ref}:${this.head ?? ''}`, 'origin', `HEAD:${this.ref}`]);
    this.head = commit;
    return commit;
  }

  close() {
    rmSync(this.directory, { recursive: true, force: true });
  }
}
