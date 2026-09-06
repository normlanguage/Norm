import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

export function report(directory, growth = 0) {
  mkdirSync(directory);
  const sha256 = 'a'.repeat(64);
  const input = { path: '/original/compiler.jar', kind: 'file', files: [{ path: 'compiler.jar', sha256, bytes: 80 }] };
  const documents = {
    'build-inputs.json': { schemaVersion: 1, arguments: ['-O2'], classpath: [input], archive: input, launcher: input },
    'size.json': { schemaVersion: 1, sha256, executableBytes: 100 + growth, deliveryBytes: 120 + growth,
      imageBytes: 100 + growth, codeBytes: 40, heapBytes: 50, reachableMethods: 3, runtimeFiles: [
        { path: 'app.exe', sha256, bytes: 100 + growth },
        { path: 'runtime.dll', sha256, bytes: 20 } ] },
    'build-output.json': { image_details: { total_bytes: 100 + growth, code_area: { bytes: 40 }, image_heap: { bytes: 50 } },
      analysis_results: { methods: { reachable: 3 } }, general_info: { java_version: '25', vendor_version: 'CE', graalvm_version: 'CE',
      c_compiler: 'cl', garbage_collector: 'Serial GC', graal_compiler: { march: 'x86-64-v3', optimization_level: '2' } } },
    'java-artifacts.json': { schemaVersion: 1, artifacts: [ { identity: 'maven:example:library:1',
      path: 'historical-cache/library.jar', sha256, bytes: 80 } ] },
    'execution-verification.json': { executableSha256: sha256, sourceSha256: 'b'.repeat(64),
      runs: [1, 2, 3].map(iteration => ({ iteration, output: 'ok' })) }
  };
  const save = () => {
    for (const [file, value] of Object.entries(documents)) writeFileSync(join(directory, file), JSON.stringify(value));
  };
  save();
  return { documents, save };
}
