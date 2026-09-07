import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { isDeepStrictEqual } from 'node:util';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readJavaArtifacts } from './native-java-inputs.mjs';
import { verifyNativeMetrics } from './native-size-metrics.mjs';
import { readBuildInputs, compareBuildInputs } from './native-build-inputs.mjs';

const metrics = ['executableBytes', 'deliveryBytes', 'codeBytes', 'heapBytes'];

function readReport(directory) {
  const read = name => JSON.parse(readFileSync(resolve(directory, name), 'utf8'));
  const size = read('size.json');
  assert.equal(size.schemaVersion, 1, 'Unsupported size report');
  assert.match(size.sha256, /^[a-f0-9]{64}$/, 'Invalid executable hash');
  for (const metric of metrics) assert.ok(Number.isSafeInteger(size[metric]) && size[metric] > 0, `Invalid ${metric}`);
  assert.ok(Array.isArray(size.runtimeFiles) && size.runtimeFiles.length > 0, 'Missing runtime delivery');
  let delivery = 0;
  let executableFound = false;
  const paths = new Set();
  for (const file of size.runtimeFiles) {
    assert.ok(typeof file.path === 'string' && file.path.length > 0 && !paths.has(file.path), 'Invalid runtime path');
    paths.add(file.path);
    assert.ok(Number.isSafeInteger(file.bytes) && file.bytes >= 0, 'Invalid runtime bytes');
    assert.match(file.sha256, /^[a-f0-9]{64}$/, 'Invalid runtime hash');
    delivery += file.bytes;
    if (file.sha256 === size.sha256 && file.bytes === size.executableBytes) executableFound = true;
  }
  assert.ok(executableFound, 'Delivery omits executable');
  assert.ok(Number.isSafeInteger(delivery), 'Delivery size overflow');
  assert.equal(delivery, size.deliveryBytes, 'Delivery size mismatch');
  const raw = read('build-output.json');
  verifyNativeMetrics(size, raw);
  const general = raw.general_info;
  const toolchain = {};
  for (const key of ['java_version', 'vendor_version', 'graalvm_version', 'c_compiler', 'garbage_collector']) {
    assert.ok(typeof general?.[key] === 'string' && general[key].length > 0, `Missing toolchain ${key}`);
    toolchain[key] = general[key];
  }
  for (const key of ['march', 'optimization_level']) {
    const value = general.graal_compiler?.[key];
    assert.ok(typeof value === 'string' && value.length > 0, `Missing compiler ${key}`);
    toolchain[key] = value;
  }
  const scope = 'execution-verification.json';
  const receipt = read(scope);
  assert.equal(receipt.executableSha256, size.sha256, 'Receipt executable mismatch');
  assert.match(receipt.sourceSha256, /^[a-f0-9]{64}$/, 'Invalid source hash');
  assert.ok(Array.isArray(receipt.runs) && receipt.runs.length >= 3, 'Three functional runs required');
  const iterations = new Set();
  let output;
  for (const run of receipt.runs) {
    assert.ok(Number.isSafeInteger(run.iteration) && run.iteration > 0 && !iterations.has(run.iteration), 'Invalid run identity');
    iterations.add(run.iteration);
    assert.equal(typeof run.output, 'string', 'Missing execution output');
    if (output === undefined) output = run.output;
    assert.equal(run.output, output, 'Inconsistent execution output');
  }
  return { size, buildInputs: readBuildInputs(directory), inputs: { toolchain, sourceSha256: receipt.sourceSha256, scope, output, java: readJavaArtifacts(directory) } };
}

export function compareNativeSize(baselineDirectory, candidateDirectory, maximumGrowthBytes = 0) {
  assert.ok(Number.isSafeInteger(maximumGrowthBytes) && maximumGrowthBytes >= 0, 'Invalid byte budget');
  const baseline = readReport(baselineDirectory);
  const candidate = readReport(candidateDirectory);
  return compareReports(baseline, candidate, maximumGrowthBytes);
}

function compareReports(baseline, candidate, maximumGrowthBytes) {
  assert.deepEqual(candidate.inputs, baseline.inputs, 'Incomparable recorded inputs or verification scope');
  const deltas = Object.fromEntries(metrics.map(metric => [metric, candidate.size[metric] - baseline.size[metric]]));
  return { passed: deltas.deliveryBytes <= maximumGrowthBytes, maximumGrowthBytes, deltas,
    ...compareBuildInputs(baseline.buildInputs, candidate.buildInputs) };
}

export function readNativeSizeSet(root) {
    const reports = readdirSync(root, { withFileTypes: true }).filter(entry => entry.isDirectory())
      .sort((a, b) => a.name.localeCompare(b.name))
      .map(entry => ({ directory: entry.name, report: readReport(resolve(root, entry.name)) }));
    assert.ok(reports.length > 0, 'Empty native report set');
    return reports;
}

export function compareNativeSizeSets(baselineRoot, candidateRoot, maximumGrowthBytes = 0) {
  assert.ok(Number.isSafeInteger(maximumGrowthBytes) && maximumGrowthBytes >= 0, 'Invalid byte budget');
  const sets = [baselineRoot, candidateRoot].map(readNativeSizeSet);
  const [baselines, candidates] = sets;
  const used = new Set();
  const comparisons = candidates.map(candidate => {
    const matches = baselines.filter(baseline => isDeepStrictEqual(baseline.report.inputs, candidate.report.inputs));
    assert.ok(matches.length > 0, `No matching baseline for ${candidate.directory}`);
    assert.equal(matches.length, 1, `Ambiguous baseline for ${candidate.directory}`);
    const baseline = matches[0];
    assert.ok(!used.has(baseline.directory), `Duplicate candidate scope: ${candidate.directory}`);
    used.add(baseline.directory);
    return { baseline: baseline.directory, candidate: candidate.directory,
      ...compareReports(baseline.report, candidate.report, maximumGrowthBytes) };
  });
  assert.equal(used.size, baselines.length, 'Candidate coverage omits baseline samples');
  return { passed: comparisons.every(result => result.passed), maximumGrowthBytes, comparisons };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const args = process.argv.slice(2);
    const sets = args[0] === '--sets';
    if (sets) args.shift();
    assert.ok(args.length === 2 || args.length === 3, 'Usage: compare-native-size.mjs [--sets] baseline candidate [maximum-growth-bytes]');
    const budget = args[2];
    assert.ok(budget === undefined || /^\d+$/.test(budget), 'Invalid byte budget');
    const result = (sets ? compareNativeSizeSets : compareNativeSize)(args[0], args[1], budget === undefined ? 0 : Number(budget));
    console.log(JSON.stringify(result, null, 2));
    process.exitCode = result.passed ? 0 : 1;
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
