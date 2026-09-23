import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { closeSync, existsSync, fstatSync, openSync, readFileSync, readSync, readdirSync, realpathSync, statSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

const purposes = ['execution', 'hosted', 'tooling'];
const debianFormat = '${binary:Package}\t${Version}\t${Architecture}\t${source:Package}\t${source:Version}\t${db:Status-Status}\n';
const rpmFormat = '%{NAME}\t%{EPOCH}\t%{VERSION}\t%{RELEASE}\t%{ARCH}\t%{SOURCERPM}\n';

function coordinate(value) {
  if (typeof value !== 'string') throw new Error('Invalid coordinate');
  const parts = value.split(':');
  if (parts.length !== 3 || parts.some(part => !/^[A-Za-z0-9_][A-Za-z0-9_.+-]*$/.test(part)) || parts[0].split('.').some(part => !part)) {
    throw new Error(`Invalid coordinate: ${value}`);
  }
  return { coordinate: value, group: parts[0], artifact: parts[1], version: parts[2] };
}

export function requirements(catalog) {
  if (catalog?.schemaVersion !== 1) throw new Error('Unsupported toolchain catalog schema');
  if (!Array.isArray(catalog.artifacts) || !Array.isArray(catalog.roots) || !catalog.roots.length || !catalog.dependencies || Array.isArray(catalog.dependencies) || !catalog.purposes) {
    throw new Error('Incomplete toolchain catalog');
  }
  if (Object.keys(catalog.purposes).sort().join(',') !== [...purposes].sort().join(',')) throw new Error('Unsupported purpose domains');
  const graph = new Map(Object.entries(catalog.dependencies));
  const owners = new Map();
  const filenames = new Set();
  for (const [key, edges] of graph) {
    coordinate(key);
    if (!Array.isArray(edges)) throw new Error(`Invalid dependency edges: ${key}`);
    for (const edge of edges) {
      coordinate(edge);
      if (!graph.has(edge)) throw new Error(`Dependency absent from graph: ${edge}`);
    }
  }
  for (const artifact of catalog.artifacts) {
    if (!artifact || ['group', 'artifact', 'version'].some(key => typeof artifact[key] !== 'string')) throw new Error('Artifact requires explicit coordinate fields');
    const primary = coordinate(`${artifact.group}:${artifact.artifact}:${artifact.version}`).coordinate;
    if (typeof artifact.file !== 'string' || !/^[A-Za-z0-9_][A-Za-z0-9_.+-]*\.jar$/.test(artifact.file) || filenames.has(artifact.file)) throw new Error('Invalid or duplicate artifact filename');
    filenames.add(artifact.file);
    if (!/^[a-fA-F0-9]{64}$/.test(artifact.sha256)) throw new Error(`Invalid artifact digest: ${primary}`);
    if (!Array.isArray(artifact.components) || !artifact.components.includes(primary)) throw new Error(`Artifact must own its primary component: ${primary}`);
    for (const component of artifact.components) {
      coordinate(component);
      if (!graph.has(component)) throw new Error(`Owned component absent from graph: ${component}`);
      if (owners.has(component)) throw new Error(`Component has multiple physical owners: ${component}`);
      owners.set(component, artifact);
    }
  }
  const roots = new Set(catalog.roots);
  if (roots.size !== catalog.roots.length) throw new Error('Duplicate roots');
  for (const root of roots) if (!graph.has(root)) throw new Error(`Root absent from graph: ${root}`);
  const assignments = new Map([...graph.keys()].map(key => [key, new Set()]));
  const declaredRoots = new Set();
  for (const purpose of purposes) {
    const entries = catalog.purposes[purpose];
    if (!Array.isArray(entries)) throw new Error(`Missing purpose roots: ${purpose}`);
    for (const entry of entries) {
      if (!roots.has(entry)) throw new Error(`Purpose entry is not a root: ${entry}`);
      declaredRoots.add(entry);
    }
    const pending = [...entries];
    const visited = new Set();
    while (pending.length) {
      const next = pending.pop();
      if (visited.has(next)) continue;
      visited.add(next);
      assignments.get(next).add(purpose);
      pending.push(...graph.get(next));
      if (owners.has(next)) pending.push(...owners.get(next).components);
    }
  }
  if (declaredRoots.size !== roots.size) throw new Error('Every root requires an explicit purpose');
  for (const [key, assigned] of assignments) if (!assigned.size) throw new Error(`Unreachable graph component: ${key}`);
  return [...graph.keys()].sort().map(key => ({ ...coordinate(key), kind: owners.has(key) ? 'jar' : 'metadata', purposes: [...assignments.get(key)].sort() }));
}

export function runCommand(program, args, { timeout = 15000 } = {}) {
  const result = spawnSync(program, args, { encoding: 'utf8', timeout, maxBuffer: 64 * 1024 * 1024, shell: false, env: { ...process.env, LC_ALL: 'C', LANG: 'C' } });
  return { status: result.status, stdout: result.stdout ?? '', stderr: result.stderr ?? '', error: result.error ? String(result.error.message) : result.signal ? `Signal: ${result.signal}` : null };
}

function hashFile(path) {
  const fd = openSync(path, 'r');
  try {
    const before = fstatSync(fd);
    if (!before.isFile()) throw new Error(`Not a regular file: ${path}`);
    const hash = createHash('sha256');
    const buffer = Buffer.alloc(128 * 1024);
    for (let count; (count = readSync(fd, buffer, 0, buffer.length, null)) > 0;) hash.update(buffer.subarray(0, count));
    const after = fstatSync(fd);
    if (before.size !== after.size || before.mtimeMs !== after.mtimeMs || before.ctimeMs !== after.ctimeMs) throw new Error(`File changed during collection: ${path}`);
    return hash.digest('hex');
  } finally {
    closeSync(fd);
  }
}

function baseFinding(required) {
  const identity = coordinate(required.coordinate);
  if (['group', 'artifact', 'version'].some(key => identity[key] !== required[key]) || !['jar', 'metadata'].includes(required.kind)) throw new Error('Requirement coordinate fields are inconsistent');
  return { ...required, status: 'not-installed', compatibility: 'not-tested', repositoryAvailability: 'not-queried', candidates: [], errors: [] };
}

function commandSucceeded(result) {
  return result.status === 0 && !result.error;
}

function debianOwners(path, command) {
  const owners = [];
  const ownership = command('dpkg-query', ['-S', path]);
  if (commandSucceeded(ownership)) {
    for (const line of ownership.stdout.trim().split('\n').filter(Boolean)) {
      const separator = line.indexOf(': ');
      if (separator < 1 || line.slice(separator + 2) !== path) throw new Error(`Unrecognized package ownership: ${line}`);
      for (const name of line.slice(0, separator).split(', ')) {
        if (!/^[a-z0-9][a-z0-9+.-]*(?::[a-z0-9][a-z0-9-]*)?$/.test(name)) throw new Error(`Unrecognized Debian package name: ${name}`);
        const details = command('dpkg-query', ['-W', `-f=${debianFormat}`, name]);
        if (!commandSucceeded(details)) throw new Error(`Cannot identify package ${name}: ${details.error ?? details.stderr}`);
        for (const detail of details.stdout.trim().split('\n').filter(Boolean)) {
          const fields = detail.split('\t');
          if (fields.length !== 6 || fields[5] !== 'installed') throw new Error(`Package is not fully installed: ${name}`);
          owners.push({ package: fields[0], version: fields[1], architecture: fields[2], sourcePackage: fields[3], sourceVersion: fields[4] });
        }
      }
    }
    if (!owners.length) throw new Error(`Empty ownership result: ${path}`);
  } else if (!(ownership.status === 1 && !ownership.error && ownership.stderr.startsWith('dpkg-query: no path found matching pattern'))) {
    throw new Error(`Ownership query failed: ${ownership.error ?? (ownership.stderr || ownership.stdout)}`);
  }
  return owners;
}

export function probeDebian(required, { repository = '/usr/share/maven-repo', command = runCommand } = {}) {
  coordinate(required.coordinate);
  const result = { ...baseFinding(required), evidenceLevel: 'installed-files-and-package-ownership' };
  try {
    if (!statSync(repository).isDirectory()) throw new Error('System Maven repository is not a directory');
    const directory = join(repository, ...required.group.split('.'), required.artifact);
    if (!existsSync(directory)) return result;
    for (const entry of readdirSync(directory).sort()) {
      if (!/^[A-Za-z0-9_][A-Za-z0-9_.+-]*$/.test(entry)) continue;
      const versionDirectory = join(directory, entry);
      if (!statSync(versionDirectory).isDirectory()) continue;
      const candidate = { versionDirectory: entry, matchesRequestedVersionDirectory: entry === required.version, files: [], complete: false };
      for (const extension of required.kind === 'jar' ? ['pom', 'jar'] : ['pom']) {
        const path = join(versionDirectory, `${required.artifact}-${entry}.${extension}`);
        if (!existsSync(path)) continue;
        const realPath = realpathSync(path);
        const sha256 = hashFile(realPath);
        const owners = debianOwners(realPath, command);
        const mappingOwners = path === realPath ? owners : debianOwners(path, command);
        const file = { path, realPath, sha256, owners, mappingOwners, ownership: owners.length && mappingOwners.length ? 'package-owned' : 'unverified' };
        candidate.files.push(file);
      }
      if (!candidate.files.length) continue;
      candidate.complete = candidate.files.length === (required.kind === 'jar' ? 2 : 1);
      result.candidates.push(candidate);
    }
    if (result.candidates.some(candidate => candidate.complete && candidate.files.every(file => file.ownership === 'package-owned'))) result.status = 'installed-candidate';
    else if (result.candidates.some(candidate => candidate.complete)) result.status = 'unverified-candidate';
    else if (result.candidates.length) result.status = 'incomplete-candidate';
  } catch (error) {
    result.status = 'probe-error'; result.errors.push(error.message);
  }
  return result;
}

function rpmCandidates(capability, requestedVersion, command) {
  const providers = command('rpm', ['-q', '--whatprovides', capability, '--qf', rpmFormat]);
  if (providers.status === 1 && !providers.error && !providers.stderr.trim() && providers.stdout.trim() === `no package provides ${capability}`) return [];
  if (!commandSucceeded(providers)) throw new Error(providers.error ?? (providers.stderr || providers.stdout));
  const candidates = [];
  for (const line of providers.stdout.trim().split('\n').filter(Boolean).sort()) {
    const fields = line.split('\t');
    if (fields.length !== 6 || fields.slice(0, 5).some(field => !/^[A-Za-z0-9_()+.~^-]+$/.test(field))) throw new Error(`Invalid RPM identity: ${line}`);
    const [name, epoch, version, release, architecture, sourcePackage] = fields;
    const packageQuery = `${name}-${version}-${release}.${architecture}`;
    const provides = command('rpm', ['-q', '--provides', packageQuery]);
    if (!commandSucceeded(provides)) throw new Error(`Cannot query RPM capabilities: ${provides.error ?? provides.stderr}`);
    const prefix = `${capability} = `;
    const artifactVersions = [...new Set(provides.stdout.split('\n').filter(value => value.startsWith(prefix)).map(value => value.slice(prefix.length).trim()).filter(Boolean))].sort();
    if (!artifactVersions.length) throw new Error(`RPM provider does not expose a version for ${capability}`);
    candidates.push({ package: name, epoch, version, release, architecture, sourcePackage, artifactVersions, matchesRequestedArtifactVersion: artifactVersions.includes(requestedVersion) });
  }
  if (!candidates.length) throw new Error('RPM returned an empty successful provider query');
  return candidates;
}

export function probeFedora(required, { command = runCommand } = {}) {
  const result = { ...baseFinding(required), evidenceLevel: 'installed-rpm-capabilities', alternatives: [] };
  const capability = required.kind === 'metadata' ? `mvn(${required.group}:${required.artifact}:pom:)` : `mvn(${required.group}:${required.artifact})`;
  result.capability = capability;
  try {
    result.candidates = rpmCandidates(capability, required.version, command);
    const inventory = command('rpm', ['-qa', '--provides']);
    if (!commandSucceeded(inventory)) throw new Error(`Cannot enumerate installed RPM capabilities: ${inventory.error ?? inventory.stderr}`);
    const alternatives = new Set();
    for (const line of inventory.stdout.split('\n')) {
      const candidate = line.trim().split(/\s+/)[0];
      if (!candidate.startsWith('mvn(') || !candidate.endsWith(')')) continue;
      const parts = candidate.slice(4, -1).split(':');
      if (parts[0] !== required.group || parts[1] !== required.artifact) continue;
      const parallel = required.kind === 'metadata'
        ? parts.length === 4 && parts[2] === 'pom' && parts[3]
        : parts.length === 3 && parts[2] && !['pom', 'jar'].includes(parts[2]);
      if (parallel && candidate !== capability) alternatives.add(candidate);
    }
    for (const alternative of [...alternatives].sort()) {
      const candidates = rpmCandidates(alternative, required.version, command);
      if (!candidates.length) throw new Error(`Installed RPM capability has no provider: ${alternative}`);
      result.alternatives.push({ capability: alternative, mapping: 'not-configured', candidates });
    }
    result.status = result.candidates.length ? 'installed-candidate' : result.alternatives.length ? 'mapping-required' : 'not-installed';
  } catch (error) {
    result.status = 'probe-error'; result.errors.push(error.message);
  }
  return result;
}

export function validateEnvironment(target, osRelease) {
  if (!['debian', 'fedora'].includes(target)) throw new Error('Target must be debian or fedora');
  const fields = new Map(osRelease.split('\n').filter(line => /^[A-Z_]+=/.test(line)).map(line => {
    const offset = line.indexOf('=');
    return [line.slice(0, offset), line.slice(offset + 1).replace(/^"(.*)"$/, '$1')];
  }));
  if (fields.get('ID') !== target) throw new Error(`Collection requires an actual ${target} root; found ${fields.get('ID') ?? 'unknown'}`);
  return { id: fields.get('ID'), versionId: fields.get('VERSION_ID') ?? null, versionCodename: fields.get('VERSION_CODENAME') ?? null, prettyName: fields.get('PRETTY_NAME') ?? null };
}

export function makeReport({ target, input, environment, findings, collectedAt = new Date().toISOString() }) {
  const summary = {};
  for (const finding of findings) summary[finding.status] = (summary[finding.status] ?? 0) + 1;
  return {
    schemaVersion: 1, target, collectedAt, input, environment,
    scope: 'installed-system-root', readiness: 'not-established',
    coverage: { runtime: 'catalog', annotationProcessors: 'not-assessed', tests: 'not-assessed', buildPlugins: 'not-assessed', buildPluginDependencies: 'not-assessed', parentsAndBoms: 'not-assessed', jdkAndNativeTools: 'not-assessed', reachabilityArchive: 'not-assessed' },
    validation: { catalogSourceBinding: 'not-verified', repositoryAvailability: 'not-queried', repositorySuite: 'not-verified', packageSourceBuilds: 'not-verified', jpms: 'not-run', compatibility: 'not-run', sourceBuild: 'not-run', networkIsolation: 'not-verified', packageInstallation: 'not-run', systemLibraryUpgrade: 'not-run' },
    summary, findings: [...findings].sort((left, right) => left.coordinate < right.coordinate ? -1 : left.coordinate > right.coordinate ? 1 : 0),
  };
}

export function renderReport(report) {
  if (report?.schemaVersion !== 1 || report.scope !== 'installed-system-root' || report.readiness !== 'not-established' || !Array.isArray(report.findings) || !report.coverage || !report.validation) throw new Error('Unsupported preflight report');
  const cell = value => String(value ?? '').replaceAll('|', '\\|').replace(/[\r\n]+/g, ' ');
  const lines = [
    '# 发行版依赖预检', '',
    `目标：\`${cell(report.target)}\`；采集时间：\`${cell(report.collectedAt)}\`。`,
    `实际系统：\`${cell(report.environment?.system?.prettyName ?? 'not-collected')}\`；架构：\`${cell(report.environment?.architecture ?? 'not-collected')}\`。`,
    `范围：\`${cell(report.scope)}\`；源码构建就绪状态：\`${cell(report.readiness)}\`。`,
    `输入目录清单 SHA-256：\`${cell(report.input?.sha256)}\`。`, '',
    '本报告检查当前系统根中的已安装依赖候选，不查询远程仓库，不证明目标 suite、API/JPMS 兼容性或源码构建通过。not-installed 仅表示当前根中未找到，不表示发行版仓库缺包。', '',
    '| 坐标 | 当前根结果 | 候选包 | 兼容性 |', '| --- | --- | --- | --- |',
  ];
  for (const finding of report.findings) {
    const candidates = [...new Set((finding.candidates ?? []).flatMap(candidate => candidate.package
      ? [`${candidate.package}-${candidate.version}-${candidate.release}`]
      : candidate.files.flatMap(file => file.owners.map(owner => `${owner.package}=${owner.version}`))))];
    lines.push(`| ${cell(finding.coordinate)} | ${cell(finding.status)} | ${cell(candidates.join(', '))} | ${cell(finding.compatibility ?? 'not-tested')} |`);
  }
  const alternatives = report.findings.flatMap(finding => (finding.alternatives ?? []).map(alternative => ({ coordinate: finding.coordinate, ...alternative })));
  if (alternatives.length) {
    lines.push('', '## 非默认 Maven capability', '', '| 坐标 | Capability | 候选包 | 映射 |', '| --- | --- | --- | --- |');
    for (const alternative of alternatives) {
      const packages = alternative.candidates.map(candidate => `${candidate.package}-${candidate.version}-${candidate.release}`).join(', ');
      lines.push(`| ${cell(alternative.coordinate)} | ${cell(alternative.capability)} | ${cell(packages)} | ${cell(alternative.mapping)} |`);
    }
  }
  lines.push('', '## 覆盖范围', '', '| 输入域 | 状态 |', '| --- | --- |');
  for (const [key, value] of Object.entries(report.coverage)) lines.push(`| ${cell(key)} | ${cell(value)} |`);
  lines.push('', '## 尚未完成的验证', '', '| 检查 | 状态 |', '| --- | --- |');
  for (const [key, value] of Object.entries(report.validation)) lines.push(`| ${cell(key)} | ${cell(value)} |`);
  const errors = report.findings.flatMap(finding => (finding.errors ?? []).map(message => `${finding.coordinate}: ${message}`));
  if (errors.length) lines.push('', '## 采集错误', '', ...errors.map(error => `- ${cell(error)}`));
  return `${lines.join('\n')}\n`;
}

function main(args) {
  const usage = 'Usage:\n  node distribution-preflight.mjs collect --target debian|fedora --catalog toolchain-artifacts.json --output report.json\n  node distribution-preflight.mjs render --input report.json --output report.md\n\nCollection reads installed package evidence only. It never installs, builds, or contacts a repository.\nExit: 0 = evidence collected; 1 = invalid input/collection error; 2 = non-installed, incomplete, or unmapped candidates.\nExit 0 never certifies source-build readiness. Existing output files are not overwritten.\n';
  if (args.length === 1 && ['--help', '-h'].includes(args[0])) { process.stdout.write(usage); return 0; }
  const action = args[0];
  if (!['collect', 'render'].includes(action)) throw new Error(usage);
  const options = action === 'collect' ? { target: { type: 'string' }, catalog: { type: 'string' }, output: { type: 'string' } } : { input: { type: 'string' }, output: { type: 'string' } };
  const { values } = parseArgs({ args: args.slice(1), options, allowPositionals: false, strict: true });
  for (const key of Object.keys(options)) if (!values[key]) throw new Error(`Missing --${key}`);
  if (existsSync(values.output)) throw new Error(`Output already exists: ${values.output}`);
  if (action === 'render') {
    writeFileSync(values.output, renderReport(JSON.parse(readFileSync(values.input, 'utf8'))), { encoding: 'utf8', flag: 'wx' });
    return 0;
  }
  const osRelease = readFileSync('/etc/os-release', 'utf8');
  const system = validateEnvironment(values.target, osRelease);
  const bytes = readFileSync(values.catalog);
  const required = requirements(JSON.parse(bytes.toString('utf8')));
  const commandCache = new Map();
  const commands = [];
  const command = (program, parameters) => {
    const key = JSON.stringify([program, parameters]);
    if (!commandCache.has(key)) {
      const result = runCommand(program, parameters);
      commandCache.set(key, result);
      commands.push({ program, arguments: parameters, ...result });
    }
    return commandCache.get(key);
  };
  const inventoryCommand = values.target === 'debian' ? ['dpkg-query', ['-W', `-f=${debianFormat}`]] : ['rpm', ['-qa', '--qf', rpmFormat]];
  const inventory = command(...inventoryCommand);
  if (!commandSucceeded(inventory)) throw new Error(`Cannot read system package inventory: ${inventory.error ?? inventory.stderr}`);
  const architecture = values.target === 'debian' ? command('dpkg', ['--print-architecture']) : command('rpm', ['--eval', '%{_arch}']);
  if (!commandSucceeded(architecture) || !architecture.stdout.trim()) throw new Error('Cannot identify target architecture');
  const findings = required.map(entry => values.target === 'debian' ? probeDebian(entry, { command }) : probeFedora(entry, { command }));
  const after = runCommand(...inventoryCommand);
  if (!commandSucceeded(after) || inventory.stdout.split('\n').sort().join('\n') !== after.stdout.split('\n').sort().join('\n')) throw new Error('System package inventory changed during collection');
  const report = makeReport({
    target: values.target,
    input: { file: resolve(values.catalog), sha256: createHash('sha256').update(bytes).digest('hex'), schemaVersion: 1 },
    environment: { system, architecture: architecture.stdout.trim(), osRelease, node: process.version, installedPackageInventory: inventory.stdout.split('\n').filter(Boolean).sort(), commands },
    findings,
  });
  writeFileSync(values.output, `${JSON.stringify(report, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
  process.stdout.write(`${JSON.stringify(report.summary)}\nreadiness=${report.readiness}\n`);
  return findings.some(finding => finding.status === 'probe-error') ? 1 : findings.every(finding => finding.status === 'installed-candidate') ? 0 : 2;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { process.exitCode = main(process.argv.slice(2)); }
  catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }
}
