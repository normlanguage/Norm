import { ChildProcessWithoutNullStreams, spawn } from 'node:child_process';
import { StringDecoder } from 'node:string_decoder';
import * as vscode from 'vscode';
import { CliInvocation } from './cli-command';

export class ProcessTerminal implements vscode.Pseudoterminal {
  private readonly writeEmitter = new vscode.EventEmitter<string>();
  private readonly closeEmitter = new vscode.EventEmitter<number>();
  private readonly stdout = new TerminalTextDecoder((text) => this.writeEmitter.fire(text));
  private readonly stderr = new TerminalTextDecoder((text) => this.writeEmitter.fire(text));
  private process: ChildProcessWithoutNullStreams | undefined;
  private finished = false;

  public readonly onDidWrite = this.writeEmitter.event;
  public readonly onDidClose = this.closeEmitter.event;

  public constructor(
    private readonly invocation: CliInvocation,
    private readonly workingDirectory: string,
  ) {}

  public open(): void {
    const child = spawn(this.invocation.command, [...this.invocation.args], {
      cwd: this.workingDirectory,
      detached: process.platform !== 'win32',
      windowsHide: true,
      stdio: 'pipe',
    });
    this.process = child;
    child.stdout.on('data', (data: Buffer) => this.stdout.write(data));
    child.stderr.on('data', (data: Buffer) => this.stderr.write(data));
    child.on('error', (error) => {
      this.stderr.write(Buffer.from(error.message + '\n'));
      this.finish(1);
    });
    child.on('close', (code) => this.finish(code ?? 1));
  }

  public close(): void {
    const child = this.process;
    if (!child || child.exitCode !== null) return;
    if (process.platform === 'win32' && child.pid !== undefined) {
      const termination = spawn('taskkill.exe', ['/pid', String(child.pid), '/t', '/f'], {
        windowsHide: true,
        stdio: 'ignore',
      });
      termination.on('error', () => child.kill());
      return;
    }
    if (child.pid !== undefined) {
      try {
        process.kill(-child.pid, 'SIGTERM');
      } catch {
        child.kill('SIGTERM');
      }
    }
  }

  public handleInput(data: string): void {
    if (data === '\x03') {
      this.close();
      return;
    }
    if (this.process?.stdin.writable) this.process.stdin.write(data);
  }

  private finish(exitCode: number): void {
    if (this.finished) return;
    this.finished = true;
    this.stdout.end();
    this.stderr.end();
    this.writeEmitter.dispose();
    this.closeEmitter.fire(exitCode);
    this.closeEmitter.dispose();
  }
}

class TerminalTextDecoder {
  private readonly decoder = new StringDecoder('utf8');
  private pendingCarriageReturn = false;

  public constructor(private readonly emitText: (text: string) => void) {}

  public write(data: Buffer): void {
    this.emit(this.decoder.write(data), false);
  }

  public end(): void {
    this.emit(this.decoder.end(), true);
  }

  private emit(decoded: string, ending: boolean): void {
    let text = this.pendingCarriageReturn ? '\r' + decoded : decoded;
    this.pendingCarriageReturn = false;
    if (!ending && text.endsWith('\r')) {
      text = text.slice(0, -1);
      this.pendingCarriageReturn = true;
    }
    if (ending && this.pendingCarriageReturn) {
      text += '\r';
      this.pendingCarriageReturn = false;
    }
    if (text) this.emitText(text.replace(/(?<!\r)\n/g, '\r\n'));
  }
}
