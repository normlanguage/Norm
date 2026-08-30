import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import pngToIco from 'png-to-ico'
import sharp from 'sharp'
import { brandRoot, icoSizes, repositoryRoot, sizes } from './brand-config.mjs'

const source = await readFile(resolve(brandRoot, 'norm.svg'), 'utf8')
assert.match(source, /viewBox="0 0 512 512"/)
assert.match(source, /#3178c6/i)
assert.match(source, /#fff(?:fff)?["']/i)
assert.doesNotMatch(source, /(?:gradient|filter|mask|image)/i)

for (const size of sizes) {
  const png = await readFile(resolve(brandRoot, `norm-${size}.png`))
  assert.deepEqual([...png.subarray(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10])
  assert.equal(png.readUInt32BE(16), size)
  assert.equal(png.readUInt32BE(20), size)
  assert.deepEqual(
    png,
    await sharp(Buffer.from(source)).resize(size, size).png({ compressionLevel: 9 }).toBuffer(),
  )
}

const ico = await readFile(resolve(brandRoot, 'norm.ico'))
assert.equal(ico.readUInt16LE(0), 0)
assert.equal(ico.readUInt16LE(2), 1)
assert.deepEqual(
  Array.from({ length: ico.readUInt16LE(4) }, (_, index) => {
    const value = ico[6 + index * 16]
    return value === 0 ? 256 : value
  }),
  icoSizes,
)
assert.deepEqual(
  ico,
  await pngToIco(icoSizes.map((size) => resolve(brandRoot, `norm-${size}.png`))),
)

const extensionRoot = resolve(repositoryRoot, 'cli', 'extensions', 'vscode')
const extensionPackage = JSON.parse(await readFile(resolve(extensionRoot, 'package.json'), 'utf8'))
assert.equal(extensionPackage.icon, 'images/norm-256.png')
assert.deepEqual(extensionPackage.contributes.languages[0].icon, {
  light: './images/norm-file.png',
  dark: './images/norm-file.png',
})
assert.deepEqual(
  await readFile(resolve(extensionRoot, 'images', 'norm-256.png')),
  await readFile(resolve(brandRoot, 'norm-256.png')),
)
assert.deepEqual(
  await readFile(resolve(extensionRoot, 'images', 'norm-file.png')),
  await readFile(resolve(brandRoot, 'norm-32.png')),
)

const windowsResource = await readFile(
  resolve(repositoryRoot, 'cli', 'compiler', 'scripts', 'windows-build', 'norm.rc'),
  'utf8',
)
assert.match(windowsResource, /ICON\s+"docs\/public\/brand\/norm\.ico"/)

console.log('Norm brand assets verified.')
