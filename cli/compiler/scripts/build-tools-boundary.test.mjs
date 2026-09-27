import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import test from 'node:test';

const root = resolve(import.meta.dirname, '../../..');
const read = name => readFileSync(resolve(root, name), 'utf8');
const logic = 'gradle/build-logic/src/main/java/dev/w0fv1/norm';

test('Gradle has one build-logic owner for generators and their golden data', () => {
  for (const name of ['BuiltinAbiGenerator', 'BuildMetadataGenerator']) {
    assert.ok(existsSync(resolve(root, `${logic}/codegen/${name}.java`)));
    assert.ok(existsSync(resolve(root, `gradle/build-logic/src/test/java/dev/w0fv1/norm/codegen/${name}Test.java`)));
  }
  assert.ok(existsSync(resolve(root, 'gradle/build-logic/src/test/resources/codegen/abi-golden.json')));
  assert.ok(!existsSync(resolve(root, 'build-tools')));
  assert.ok(!existsSync(resolve(root, 'build-maven-plugin')));
});

test('Gradle included build is the only build-tool entry', () => {
  assert.match(read('settings.gradle.kts'), /includeBuild\("gradle\/build-logic"\)/);
  assert.match(read('cli/compiler/build.gradle.kts'), /norm\.compiler/);
  for (const name of ['pom.xml', 'cli/compiler/pom.xml', 'build-tools/pom.xml']) {
    assert.ok(!existsSync(resolve(root, name)), name);
  }
  assert.ok(!existsSync(resolve(root, '.mvn')));
  assert.ok(!existsSync(resolve(root, 'mvnw')));
});

test('resolved graph reaches the shared schema two catalog generator', () => {
  assert.match(read(`${logic}/packaging/ToolchainArtifactCatalogGenerator.java`), /schemaVersion", 2/);
  const plugin = read(`${logic}/gradle/NormCompilerPlugin.java`);
  const adapter = read(`${logic}/gradle/GenerateToolchainCatalog.java`);
  assert.match(plugin, /GenerateToolchainCatalog/);
  assert.match(adapter, /ToolchainArtifactCatalogGenerator\.generate/);
  assert.match(plugin, /RuntimeModuleAssembler/);
  assert.doesNotMatch(plugin, /schemaVersion", 1/);
});
