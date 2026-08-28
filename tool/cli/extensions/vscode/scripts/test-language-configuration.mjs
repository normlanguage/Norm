import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const grammar = JSON.parse(readFileSync('syntaxes/norm.tmLanguage.json', 'utf8'));
const configuration = JSON.parse(readFileSync('language-configuration.json', 'utf8'));
const extension = JSON.parse(readFileSync('package.json', 'utf8'));
const typePattern = new RegExp(grammar.repository.types.match);
const genericPattern = new RegExp(grammar.repository.generics.patterns[0].begin);
const numericPattern = new RegExp(grammar.repository.numbers.match);
const constantPattern = new RegExp(grammar.repository.constants.match);
const operatorPattern = new RegExp(grammar.repository.operators.match);
const keywordPattern = new RegExp(grammar.repository.keywords.match);
const modifierPattern = new RegExp(grammar.repository.modifiers.match);
const declarationPattern = new RegExp(grammar.repository.declarations.patterns[0].match);

for (const type of [
  'Integer',
  'Long',
  'Float',
  'Double',
  'Number',
  'Boolean',
  'String',
  'Void',
  'CodePoint',
  'Stringable',
  'Field',
  'ReflectedValue',
  'FunctionContext',
  'JsonMapper',
  'XmlMapper',
  'YamlMapper',
  'Array<CodePoint>',
]) {
  assert.match(type, typePattern);
}
for (const invalidType of ['int', 'bool', 'void']) {
  assert.doesNotMatch(invalidType, typePattern);
}
assert.match('Array<CodePoint>', genericPattern);
assert.match('List<>', genericPattern);
assert.doesNotMatch('Array<int>', genericPattern);
for (const number of ['7', '2_147_483_648', '3.14', '1.25e-3']) {
  assert.match(number, numericPattern);
}
assert.match('null', constantPattern);
assert.match('?.', operatorPattern);
assert.match('??', operatorPattern);
assert.match('?', operatorPattern);
assert.match('switch', keywordPattern);
assert.match('case', keywordPattern);
assert.match('interface', keywordPattern);
assert.match('var', keywordPattern);
assert.match('implements', keywordPattern);
assert.match('extends', keywordPattern);
assert.match('try', keywordPattern);
assert.match('catch', keywordPattern);
assert.match('finally', keywordPattern);
assert.match('throw', keywordPattern);
assert.match('public', modifierPattern);
assert.match('private', modifierPattern);
assert.match('extension', modifierPattern);
for (const declaration of [
  'class Counter',
  'value Point',
  'enum Result',
  'interface Named',
  'annotation Log implements FunctionInterceptor',
]) {
  assert.match(declaration, declarationPattern);
}
for (const expression of ['Integer value', 'value = next', 'String annotation']) {
  assert.doesNotMatch(expression, declarationPattern);
}
assert.equal(
  grammar.repository.declarations.patterns[0].captures['1'].name,
  'keyword.declaration.type.norm',
);
assert.equal(grammar.repository.generics.patterns.length, 1);

assert.equal(grammar.repository.codePoints.name, 'constant.character.norm');
assert.equal(grammar.repository.codePoints.begin, "'");
assert.equal(grammar.repository.codePoints.end, "'");
assert.ok(grammar.patterns.some((pattern) => pattern.include === '#codePoints'));
assert.ok(
  configuration.autoClosingPairs.some((pair) => pair.open === "'" && pair.close === "'"),
);
assert.ok(configuration.surroundingPairs.some(([open, close]) => open === "'" && close === "'"));
assert.equal(grammar.repository.comments, undefined);
assert.equal(configuration.comments, undefined);
assert.equal(
  extension.contributes.configurationDefaults['[norm]']['editor.defaultFormatter'],
  'normlang.norm-language-support',
);
assert.equal(extension.contributes.configurationDefaults['[norm]']['editor.formatOnSave'], true);

const projectVersion = /^normVersion=(\d+\.\d+\.\d+)(?:-SNAPSHOT)?$/m.exec(
  readFileSync('../../../../gradle.properties', 'utf8'),
)?.[1];
assert.ok(projectVersion, 'gradle.properties does not declare a semantic Norm version');
assert.equal(extension.version, projectVersion, 'extension version must track the Norm version');

console.log('Norm language configuration tests succeeded.');
