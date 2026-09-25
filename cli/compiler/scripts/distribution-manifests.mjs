import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { releaseTargets, releaseVersion } from './release-model.mjs';
import { verifiedReleaseAsset } from './release-assets.mjs';

export function generateManifests(version, assetsDirectory, outputDirectory) {
  releaseVersion(version);
  const homepage = 'https://github.com/normlanguage/Norm';
  const baseUrl = `${homepage}/releases/download/v${version}`;
  const description = 'Statically typed programming language and toolchain';
  const assets = new Map(releaseTargets.map(({ target }) => {
    const { name, hash } = verifiedReleaseAsset(version, target, assetsDirectory);
    return [target, { url: `${baseUrl}/${name}`, hash }];
  }));
  const windows = assets.get('win32-x64');
  if (!windows) throw new Error('Windows x64 release asset is required');
  const linux = assets.get('linux-x64');
  if (!linux) throw new Error('Linux x64 release asset is required');
  const files = new Map();
  const formula = [
    'class Normlang < Formula',
    `  desc "${description}"`,
    `  homepage "${homepage}"`,
    `  version "${version}"`,
    '  license "MPL-2.0"',
  ];
  for (const [platform, condition] of [['darwin', 'on_macos'], ['linux', 'on_linux']]) {
    const platformAssets = [...assets].filter(([target]) => target.startsWith(platform + '-'));
    if (platformAssets.length === 0) throw new Error(`Missing ${platform} release asset`);
    formula.push('', `  ${condition} do`);
    if (platformAssets.length === 1) formula.push(`    depends_on arch: :${platformAssets[0][0].endsWith('-arm64') ? 'arm64' : 'x86_64'}`);
    for (const [target, asset] of platformAssets) {
      const architecture = target.endsWith('-arm64') ? 'on_arm' : 'on_intel';
      formula.push(`    ${architecture} do`, `      url "${asset.url}"`, `      sha256 "${asset.hash}"`, '    end');
    }
    formula.push('  end');
  }
  const fixture = readFileSync(new URL('./fixtures/hello.norm', import.meta.url), 'utf8').trim();
  formula.push('', '  def install', '    libexec.install Dir["*"]', '    bin.write_exec_script libexec/"bin/norm"', '  end', '', '  test do', '    (testpath/"hello.norm").write <<~NORM', ...fixture.split(/\r?\n/).map(line => `      ${line}`), '    NORM', '    assert_equal "Hello from Norm\\n", shell_output("#{bin}/norm run hello.norm")', '    assert_equal "norm #{version}\\n", shell_output("#{bin}/norm --version")', '  end', 'end', '');
  files.set('homebrew-tap/Formula/normlang.rb', formula.join('\n'));
  files.set('snap/snapcraft.yaml', [
    'name: normlang',
    'title: Norm',
    `version: '${version}'`,
    `summary: ${description}`,
    'description: |',
    '  Norm provides source execution, type checking, tests, package resolution,',
    '  application builds and a language server.',
    'license: MPL-2.0',
    `contact: ${homepage}/issues`,
    `issues: ${homepage}/issues`,
    `source-code: ${homepage}`,
    `website: ${homepage}`,
    'base: core24',
    'grade: stable',
    'confinement: classic',
    'platforms:',
    '  amd64:',
    'apps:',
    '  norm:',
    '    command: bin/norm',
    'parts:',
    '  norm:',
    '    plugin: dump',
    '    build-attributes:',
    '      - enable-patchelf',
    '    stage-packages:',
    '      - libasound2t64',
    '      - libx11-6',
    '      - libxext6',
    '      - libxi6',
    '      - libxrender1',
    '      - libxtst6',
    `    source: ${linux.url}`,
    `    source-checksum: sha256/${linux.hash}`,
    '',
  ].join('\n'));
  files.set('scoop-bucket/bucket/normlang.json', JSON.stringify({
    version,
    description,
    homepage,
    license: 'MPL-2.0',
    architecture: { '64bit': windows },
    bin: 'norm.exe',
    checkver: { github: homepage },
    autoupdate: { architecture: { '64bit': { url: `${homepage}/releases/download/v$version/norm.exe`, hash: { url: '$baseurl/SHA256SUMS' } } } },
  }, null, 2) + '\n');
  const identifier = 'NormLanguage.Norm';
  const winget = `winget/manifests/n/NormLanguage/Norm/${version}/${identifier}`;
  const common = `PackageIdentifier: ${identifier}\nPackageVersion: ${version}\n`;
  files.set(`${winget}.yaml`, common + 'DefaultLocale: en-US\nManifestType: version\nManifestVersion: 1.12.0\n');
  files.set(`${winget}.locale.en-US.yaml`, common + `PackageLocale: en-US\nPublisher: NormLanguage\nPublisherUrl: https://github.com/normlanguage\nPublisherSupportUrl: ${homepage}/issues\nPackageName: Norm\nPackageUrl: ${homepage}\nLicense: MPL-2.0\nLicenseUrl: ${homepage}/blob/v${version}/LICENSE\nShortDescription: ${description}\nMoniker: norm\nReleaseNotesUrl: ${homepage}/releases/tag/v${version}\nManifestType: defaultLocale\nManifestVersion: 1.12.0\n`);
  files.set(`${winget}.installer.yaml`, common + `InstallerType: portable\nCommands:\n- norm\nInstallers:\n- Architecture: x64\n  InstallerUrl: ${windows.url}\n  InstallerSha256: ${windows.hash.toUpperCase()}\nManifestType: installer\nManifestVersion: 1.12.0\n`);
  for (const [relative, contents] of files) {
    const destination = join(outputDirectory, relative);
    mkdirSync(dirname(destination), { recursive: true });
    const schema = relative.startsWith('winget/') ? `# yaml-language-server: $schema=https://aka.ms/winget-manifest.${/^ManifestType: (.+)$/m.exec(contents)[1]}.1.12.0.schema.json\n\n` : '';
    writeFileSync(destination, schema + contents);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.length !== 5) throw new Error('Usage: distribution-manifests.mjs <version> <release-assets> <output>');
  generateManifests(process.argv[2], process.argv[3], process.argv[4]);
}
