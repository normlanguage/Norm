import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import test from 'node:test';

const root = resolve(import.meta.dirname, '../../..');
const read = name => readFileSync(resolve(root, name), 'utf8');
const generator = 'dev/w0fv1/norm/codegen/BuiltinAbiGenerator.java';

test('Maven has one build-tools generator and golden owner', () => {
  assert.ok(existsSync(resolve(root, 'build-tools/src/main/java', generator)));
  assert.ok(!existsSync(resolve(root, 'cli/compiler/src/codegen/java', generator)));
  assert.ok(existsSync(resolve(root, 'build-tools/src/test/java/dev/w0fv1/norm/codegen/BuiltinAbiGeneratorTest.java')));
  assert.ok(existsSync(resolve(root, 'build-tools/src/test/resources/codegen/abi-golden.json')));
  assert.ok(!existsSync(resolve(root, 'cli/compiler/src/test/resources/codegen/abi-golden.json')));
  assert.match(read('pom.xml'), /<module>build-tools<\/module>/);
  assert.match(read('cli/compiler/pom.xml'), /<mainClass>dev\.w0fv1\.norm\.codegen\.BuiltinAbiGenerator<\/mainClass>/);
});

test('build-tools does not acquire product runtime dependencies', () => {
  const pom = read('build-tools/pom.xml');
  const dependencies = pom.match(/<dependencies>[\s\S]*?<\/dependencies>/)?.[0];
  assert.ok(dependencies);
  assert.doesNotMatch(dependencies, /<artifactId>compiler<\/artifactId>|org\.graalvm/);
  assert.doesNotMatch(read('build-tools/src/main/java/' + generator), /import dev\.w0fv1\.norm\.(?!codegen)/);
  assert.ok(!existsSync(resolve(root, 'build-tools/src/main/java/module-info.java')));
});

test('build metadata generation uses the same Maven build-tools boundary', () => {
  assert.ok(existsSync(resolve(root, 'build-tools/src/main/java/dev/w0fv1/norm/codegen/BuildMetadataGenerator.java')));
  assert.ok(existsSync(resolve(root, 'build-tools/src/test/java/dev/w0fv1/norm/codegen/BuildMetadataGeneratorTest.java')));
  assert.match(read('cli/compiler/pom.xml'), /<mainClass>dev\.w0fv1\.norm\.codegen\.BuildMetadataGenerator<\/mainClass>/);
});

test('toolchain CI observes the Maven build inputs', () => {
  const workflow = read('.github/workflows/toolchain.yml');
  for (const path of ['build-tools/**', 'build-maven-plugin/**', '.mvn/**', 'pom.xml', 'mvnw']) {
    assert.equal(workflow.split(`- '${path}'`).length - 1, 2, path);
  }
});
