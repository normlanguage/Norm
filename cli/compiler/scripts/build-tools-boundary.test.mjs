import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import test from 'node:test';

const root = resolve(import.meta.dirname, '../../..');
const read = name => readFileSync(resolve(root, name), 'utf8');
const generator = 'dev/w0fv1/norm/codegen/BuiltinAbiGenerator.java';

test('Maven and Gradle share the single build-tools generator source', () => {
  assert.ok(existsSync(resolve(root, 'build-tools/src/main/java', generator)));
  assert.ok(!existsSync(resolve(root, 'cli/compiler/src/codegen/java', generator)));
  assert.match(read('pom.xml'), /<module>build-tools<\/module>/);
  assert.match(read('cli/compiler/build.gradle.kts'), /build-tools\/src\/main\/java/);
  assert.match(read('cli/compiler/scripts/verify-codegen.mjs'), /build-tools\/src\/main\/java/);
});

test('build-tools does not acquire the product or its runtime dependencies', () => {
  const pom = read('build-tools/pom.xml');
  const dependencies = pom.match(/<dependencies>[\s\S]*?<\/dependencies>/)?.[0];
  assert.ok(dependencies);
  assert.doesNotMatch(dependencies, /<version>|<artifactId>compiler<\/artifactId>|org\.graalvm/);
  assert.doesNotMatch(read('build-tools/src/main/java/' + generator), /import dev\.w0fv1\.norm\./);
  assert.ok(!existsSync(resolve(root, 'build-tools/src/main/java/module-info.java')));
});

test('generator tests and golden bytes have one owner shared with Gradle', () => {
  assert.ok(existsSync(resolve(root, 'build-tools/src/test/java/dev/w0fv1/norm/codegen/BuiltinAbiGeneratorTest.java')));
  assert.ok(existsSync(resolve(root, 'build-tools/src/test/resources/codegen/abi-golden.json')));
  assert.ok(!existsSync(resolve(root, 'cli/compiler/src/test/resources/codegen/abi-golden.json')));
  const gradle = read('cli/compiler/build.gradle.kts');
  assert.match(gradle, /build-tools\/src\/test\/java/);
  assert.match(gradle, /build-tools\/src\/test\/resources/);
  assert.match(read('cli/compiler/scripts/verify-codegen.mjs'), /build-tools\/src\/test\/resources\/codegen\/abi-golden.json/);
});

test('build metadata generation has one implementation in build-tools', () => {
  assert.ok(existsSync(resolve(root, 'build-tools/src/main/java/dev/w0fv1/norm/codegen/BuildMetadataGenerator.java')));
  assert.ok(existsSync(resolve(root, 'build-tools/src/test/java/dev/w0fv1/norm/codegen/BuildMetadataGeneratorTest.java')));
  const gradle = read('cli/compiler/build.gradle.kts');
  assert.match(gradle, /mainClass\.set\("dev\.w0fv1\.norm\.codegen\.BuildMetadataGenerator"\)/);
  assert.doesNotMatch(gradle, /abstract class GenerateBuildMetadata/);
});

test('toolchain CI observes both build-tools sources and root Maven configuration', () => {
  const workflow = read('.github/workflows/toolchain.yml');
  assert.equal(workflow.match(/- 'build-tools\/\*\*'/g)?.length, 2);
  assert.equal(workflow.match(/- 'build-maven-plugin\/\*\*'/g)?.length, 2);
  assert.equal(workflow.match(/- 'pom.xml'/g)?.length, 2);
});

test('transitional Maven Java, Gson and JUnit declarations match the product build', () => {
  const pom = read('pom.xml');
  const versions = read('gradle/libs.versions.toml');
  for (const [property, key] of [['maven.compiler.release', 'java'], ['gson.version', 'gson'], ['junit.version', 'junit']]) {
    const maven = pom.match(new RegExp(`<${property.replaceAll('.', '\\.')}>([^<]+)</`))?.[1];
    const gradle = versions.match(new RegExp(`^${key} = "([^"]+)"`, 'm'))?.[1];
    assert.ok(maven && gradle, property);
    assert.equal(maven, gradle, `${property} must not drift while both entries are active`);
  }
});
