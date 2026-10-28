import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import sharp from 'sharp'
import { brandRoot, icoSizes, repositoryRoot, sizes } from './brand-config.mjs'

const pngSignature = [137, 80, 78, 71, 13, 10, 26, 10]
const crcTable = Array.from({ length: 256 }, (_, value) => {
  let crc = value
  for (let bit = 0; bit < 8; bit += 1) {
    crc = crc & 1 ? 0xedb88320 ^ (crc >>> 1) : crc >>> 1
  }
  return crc >>> 0
})

const crc32 = (bytes) => {
  let crc = 0xffffffff
  for (const byte of bytes) crc = crcTable[(crc ^ byte) & 0xff] ^ (crc >>> 8)
  return (crc ^ 0xffffffff) >>> 0
}

const checkPng = (png, width, height) => {
  assert.deepEqual([...png.subarray(0, 8)], pngSignature)
  let offset = 8
  let hasHeader = false
  let hasData = false
  let hasEnd = false
  while (offset < png.length) {
    assert.ok(offset + 12 <= png.length)
    const length = png.readUInt32BE(offset)
    const typeStart = offset + 4
    const dataStart = offset + 8
    const end = dataStart + length
    assert.ok(end + 4 <= png.length)
    const type = png.toString('ascii', typeStart, dataStart)
    const chunkEnd = end + 4
    assert.equal(png.readUInt32BE(end), crc32(png.subarray(typeStart, end)))
    if (type === 'IHDR') {
      assert.equal(length, 13)
      assert.equal(png.readUInt32BE(dataStart), width)
      assert.equal(png.readUInt32BE(dataStart + 4), height)
      assert.equal(hasHeader, false)
      hasHeader = true
    } else if (type === 'IDAT') {
      hasData = true
    } else if (type === 'IEND') {
      assert.equal(length, 0)
      assert.equal(chunkEnd, png.length)
      hasEnd = true
    }
    offset = chunkEnd
  }
  assert.equal(offset, png.length)
  assert.equal(hasHeader, true)
  assert.equal(hasData, true)
  assert.equal(hasEnd, true)
}

const source = await readFile(resolve(brandRoot, 'norm.svg'), 'utf8')
assert.match(source, /viewBox="0 0 512 512"/)
assert.match(source, /#3178c6/i)
assert.match(source, /#fff(?:fff)?["']/i)
assert.doesNotMatch(source, /(?:gradient|filter|mask|image)/i)

for (const size of sizes) {
  const png = await readFile(resolve(brandRoot, `norm-${size}.png`))
  checkPng(png, size, size)
  assert.deepEqual(
    png,
    await sharp(Buffer.from(source)).resize(size, size).png({ compressionLevel: 9 }).toBuffer(),
  )
}

const ico = await readFile(resolve(brandRoot, 'norm.ico'))
assert.equal(ico.readUInt16LE(0), 0)
assert.equal(ico.readUInt16LE(2), 1)
const icoCount = ico.readUInt16LE(4)
assert.equal(icoCount, icoSizes.length)
for (let index = 0; index < icoCount; index += 1) {
  const directoryOffset = 6 + index * 16
  const width = ico[directoryOffset] || 256
  const height = ico[directoryOffset + 1] || 256
  const size = icoSizes[index]
  assert.equal(width, size)
  assert.equal(height, size)
  assert.equal(ico[directoryOffset + 2], 0)
  assert.equal(ico[directoryOffset + 3], 0)
  assert.equal(ico.readUInt16LE(directoryOffset + 4), 1)
  assert.equal(ico.readUInt16LE(directoryOffset + 6), 32)
  const byteLength = ico.readUInt32LE(directoryOffset + 8)
  const imageOffset = ico.readUInt32LE(directoryOffset + 12)
  const dibHeaderLength = 40
  const pixelLength = size * size * 4
  const maskRowLength = Math.ceil(size / 32) * 4
  const maskLength = maskRowLength * size
  assert.equal(byteLength, dibHeaderLength + pixelLength + maskLength)
  assert.ok(imageOffset >= 6 + icoCount * 16)
  assert.ok(imageOffset + byteLength <= ico.length)
  assert.equal(ico.readUInt32LE(imageOffset), dibHeaderLength)
  assert.equal(ico.readInt32LE(imageOffset + 4), size)
  assert.equal(ico.readInt32LE(imageOffset + 8), size * 2)
  assert.equal(ico.readUInt16LE(imageOffset + 12), 1)
  assert.equal(ico.readUInt16LE(imageOffset + 14), 32)
  assert.equal(ico.readUInt32LE(imageOffset + 16), 0)
}

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



const banner = await readFile(resolve(brandRoot, 'norm-banner.svg'), 'utf8')
assert.match(banner, /viewBox="0 0 1280 400"/)
const logoMarkup = source.trim().slice(source.indexOf('>') + 1, source.lastIndexOf('</svg>')).trim()
assert.ok(banner.includes(logoMarkup))
assert.equal((banner.match(/<svg\b/g) ?? []).length, 1)
assert.doesNotMatch(banner, /https?:\/\/(?!www.w3.org)/)
const bannerPng = await readFile(resolve(brandRoot, 'norm-banner.png'))
checkPng(bannerPng, 1280, 400)

console.log('Norm brand assets verified.')
