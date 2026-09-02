import { execFile } from 'node:child_process';
import { accessSync, constants } from 'node:fs';
import { delimiter, isAbsolute, join, resolve } from 'node:path';

export interface CliInvocation {
  readonly command: string;
  readonly args: readonly string[];
}

export type CliSource = 'configured' | 'bundled' | 'workspace' | 'development' | 'path';

export interface CliResolutionOptions {
  readonly configured: string;
  readonly workspacePath: string | undefined;
  readonly extensionPath: string;
  readonly extensionVersion: string;
  readonly development: boolean;
}

export interface ResolvedCliCommand {
  readonly command: string;
  readonly version: string;
  readonly source: CliSource;
}

export type CliRejectionReason = 'not-found' | 'version-unavailable' | 'version-mismatch';

export interface CliRejection {
  readonly command: string;
  readonly source: CliSource;
  readonly reason: CliRejectionReason;
  readonly version?: string;
}

export interface CliResolution {
  readonly selected: ResolvedCliCommand | undefined;
  readonly rejected: readonly CliRejection[];
}

export type CliVersionProbe = (command: string) => Promise<string | undefined>;

interface CliCandidate {
  readonly command: string;
  readonly source: CliSource;
}

export async function resolveCliCommand(
  options: CliResolutionOptions,
  probeVersion: CliVersionProbe = readCliVersion,
): Promise<CliResolution> {
  const rejected: CliRejection[] = [];
  const requested = options.configured.trim();
  const configured = requested ? configuredCandidate(requested, options) : undefined;
  if (requested) {
    if (!configured) {
      rejected.push({ command: requested, source: 'configured', reason: 'not-found' });
    }
  }

  const executable = process.platform === 'win32' ? 'norm.bat' : 'norm';
  const bundledExecutable = process.platform === 'win32' ? 'norm.exe' : 'norm';
  const workspace: CliCandidate[] = options.workspacePath
    ? [
        {
          command: join(
            options.workspacePath,
            'cli',
            'compiler',
            'build',
            'install',
            'norm',
            'bin',
            executable,
          ),
          source: 'workspace' as const,
        },
      ]
    : [];
  const development: CliCandidate[] = [
    {
      command: resolve(
        options.extensionPath,
        '..',
        '..',
        'compiler',
        'build',
        'install',
        'norm',
        'bin',
        executable,
      ),
      source: 'development',
    },
  ];
  const bundled: CliCandidate[] = [
    {
      command: join(options.extensionPath, 'server', 'bin', executable),
      source: 'bundled',
    },
    {
      command: join(
        options.extensionPath,
        'bin',
        `${process.platform}-${process.arch}`,
        bundledExecutable,
      ),
      source: 'bundled',
    },
  ];
  const path = executableOnPath('norm');
  const discovered = options.development
    ? [...workspace, ...development, ...bundled]
    : [...workspace, ...bundled];
  const candidates = [
    ...(configured ? [configured] : []),
    ...discovered,
    ...(path ? [{ command: path, source: 'path' as const }] : []),
  ];
  const seen = new Set<string>();
  for (const candidate of candidates) {
    if (!isExecutable(candidate.command)) continue;
    const identity =
      process.platform === 'win32'
        ? resolve(candidate.command).toLowerCase()
        : resolve(candidate.command);
    if (seen.has(identity)) continue;
    seen.add(identity);
    const version = await probeVersion(candidate.command);
    if (!version) {
      rejected.push({ ...candidate, reason: 'version-unavailable' });
      continue;
    }
    if (!compatibleVersion(version, options.extensionVersion)) {
      rejected.push({ ...candidate, reason: 'version-mismatch', version });
      continue;
    }
    return { selected: { ...candidate, version }, rejected };
  }
  return { selected: undefined, rejected };
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

function configuredCandidate(
  requested: string,
  options: CliResolutionOptions,
): CliCandidate | undefined {
  const command =
    isAbsolute(requested) ||
    requested.includes('/') ||
    requested.includes('\\')
      ? resolve(options.workspacePath ?? options.extensionPath, requested)
      : executableOnPath(requested);
  return command && isExecutable(command) ? { command, source: 'configured' } : undefined;
}

function readCliVersion(command: string): Promise<string | undefined> {
  const invocation = cliInvocation(command, ['--version']);
  return new Promise((complete) => {
    execFile(
      invocation.command,
      [...invocation.args],
      { encoding: 'utf8', timeout: 5_000, windowsHide: true },
      (error, stdout) => {
        if (error) return complete(undefined);
        const match = /^norm\s+([^\r\n]+)$/u.exec(stdout.trim());
        complete(match?.[1]);
      },
    );
  });
}

function compatibleVersion(actual: string, expected: string): boolean {
  return actual === expected || actual === `${expected}-SNAPSHOT`;
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
