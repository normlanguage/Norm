import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const grammar = JSON.parse(readFileSync('syntaxes/norm.tmLanguage.json', 'utf8'));
const configuration = JSON.parse(readFileSync('language-configuration.json', 'utf8'));
const typePattern = new RegExp(grammar.repository.types.match);
const genericPattern = new RegExp(grammar.repository.generics.patterns[0].begin);
const constantPattern = new RegExp(grammar.repository.constants.match);
const operatorPattern = new RegExp(grammar.repository.operators.match);

for (const type of ['Integer', 'Boolean', 'String', 'Void', 'CodePoint', 'Array<CodePoint>']) {
  assert.match(type, typePattern);
}
for (const legacyType of ['int', 'bool', 'void']) {
  assert.doesNotMatch(legacyType, typePattern);
}
assert.match('Array<CodePoint>', genericPattern);
assert.doesNotMatch('Array<int>', genericPattern);
assert.match('null', constantPattern);
assert.match('?.', operatorPattern);
assert.match('??', operatorPattern);
assert.match('?', operatorPattern);

assert.equal(grammar.repository.codePoints.name, 'constant.character.norm');
assert.equal(grammar.repository.codePoints.begin, "'");
assert.equal(grammar.repository.codePoints.end, "'");
assert.ok(grammar.patterns.some((pattern) => pattern.include === '#codePoints'));
assert.ok(
  configuration.autoClosingPairs.some((pair) => pair.open === "'" && pair.close === "'"),
);
assert.ok(configuration.surroundingPairs.some(([open, close]) => open === "'" && close === "'"));

console.log('Norm language configuration tests succeeded.');
