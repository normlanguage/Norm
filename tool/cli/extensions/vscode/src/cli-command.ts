import { accessSync, constants } from 'node:fs';
import { delimiter, isAbsolute, join, resolve, sep } from 'node:path';

export interface CliInvocation {
  readonly command: string;
  readonly args: readonly string[];
}

export function resolveCliCommand(
  configured: string,
  workspacePath: string | undefined,
  extensionPath: string,
): string | undefined {
  const requested = configured.trim();
  if (requested) {
    const configuredCandidate =
      isAbsolute(requested) || requested.includes(sep)
        ? resolve(workspacePath ?? extensionPath, requested)
        : executableOnPath(requested);
    return configuredCandidate && isExecutable(configuredCandidate) ? configuredCandidate : undefined;
  }

  const executable = process.platform === 'win32' ? 'norm.bat' : 'norm';
  const bundledExecutable = process.platform === 'win32' ? 'norm.exe' : 'norm';
  const environment = (process.env.NORM_CLI ?? '').trim();
  const environmentCandidate = environment
    ? isAbsolute(environment) || environment.includes(sep)
      ? resolve(environment)
      : executableOnPath(environment)
    : undefined;
  const candidates = [
    environmentCandidate,
    join(extensionPath, 'bin', `${process.platform}-${process.arch}`, bundledExecutable),
    workspacePath &&
      join(workspacePath, 'tool', 'cli', 'app', 'build', 'install', 'norm', 'bin', executable),
    resolve(extensionPath, '..', '..', 'app', 'build', 'install', 'norm', 'bin', executable),
    executableOnPath('norm'),
  ];
  return candidates.find(
    (candidate): candidate is string => Boolean(candidate && isExecutable(candidate)),
  );
}

export function cliInvocation(command: string, args: readonly string[]): CliInvocation {
  if (process.platform === 'win32' && /\.(?:bat|cmd)$/i.test(command)) {
    return {
      command: process.env.ComSpec ?? 'cmd.exe',
      args: ['/d', '/c', 'call', command, ...args],
    };
  }
  return { command, args };
}

function executableOnPath(name: string): string | undefined {
  const extensions =
    process.platform === 'win32'
      ? (process.env.PATHEXT ?? '.COM;.EXE;.BAT;.CMD').split(';')
      : [''];
  const hasExtension = extensions.some((extension) =>
    name.toUpperCase().endsWith(extension.toUpperCase()),
  );
  for (const directory of (process.env.PATH ?? '').split(delimiter)) {
    if (!directory) continue;
    const names = hasExtension
      ? [name]
      : extensions.map((extension) => name + extension.toLowerCase());
    for (const candidateName of names) {
      const candidate = join(directory, candidateName);
      if (isExecutable(candidate)) return candidate;
    }
  }
  return undefined;
}

function isExecutable(path: string): boolean {
  try {
    accessSync(path, process.platform === 'win32' ? constants.F_OK : constants.X_OK);
    return true;
  } catch {
    return false;
  }
}
