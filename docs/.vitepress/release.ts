export const currentRelease = '0.11'

const versions = ['0.11', '0.10', '0.9', '0.8', '0.7', '0.6', '0.5', '0.4', '0.3', '0.2', '0.1']

export function releaseItems(prefix: string) {
  return versions.map((version) => ({
    text: `Norm ${version}`,
    link: `${prefix}/${version}`,
  }))
}
