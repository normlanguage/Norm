import { spawn, execFile } from 'node:child_process';
import { once } from 'node:events';
import { writeFile } from 'node:fs/promises';
import { performance } from 'node:perf_hooks';
import { parseArgs, promisify } from 'node:util';
import { pathToFileURL } from 'node:url';

export async function measureStartup({ command, args, cwd, timeoutMs = 60000 }) {
  const started = performance.now();
  const child = spawn(command, args, { cwd, windowsHide: true, detached: process.platform !== 'win32', stdio: ['ignore', 'pipe', 'pipe'] });
  const output = [];
  const closed = once(child, 'close');
  closed.catch(() => {});
  let timer;
  let firstOutputMs;
  let announced = false;
  try {
    return await new Promise((resolve, reject) => {
      timer = setTimeout(() => reject(new Error(`Startup timed out after ${timeoutMs} ms`)), timeoutMs);
      child.once('error', reject);
      child.once('close', code => reject(new Error(`Application exited before HTTP readiness: ${code}\n${output.map(event => event.text).join('')}`)));
      for (const stream of ['stdout', 'stderr']) {
        let pending = '';
        child[stream].setEncoding('utf8');
        child[stream].on('data', text => {
          const elapsedMs = performance.now() - started;
          firstOutputMs ??= elapsedMs;
          output.push({ elapsedMs, stream, text });
          pending += text;
          const lines = pending.split(/\r?\n/);
          pending = lines.pop();
          for (const line of lines) {
            const address = line.trim();
            if (announced || !/^http:\/\/127\.0\.0\.1:\d+\/$/.test(address)) continue;
            announced = true;
            fetch(address, { signal: AbortSignal.timeout(Math.max(1, timeoutMs - Math.ceil(elapsedMs))) })
              .then(async response => {
                await response.arrayBuffer();
                if (response.status !== 200) throw new Error(`HTTP readiness returned ${response.status}`);
                resolve({ firstOutputMs, addressAnnouncedMs: elapsedMs, httpReadyMs: performance.now() - started, address, status: response.status, output });
              })
              .catch(reject);
          }
        });
      }
    });
  } finally {
    clearTimeout(timer);
    if (child.pid && child.exitCode === null && child.signalCode === null) {
      if (process.platform === 'win32') {
        await promisify(execFile)('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true });
      } else {
        process.kill(-child.pid, 'SIGKILL');
      }
    }
    await closed;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const { values, positionals } = parseArgs({
    options: { command: { type: 'string' }, cwd: { type: 'string' }, output: { type: 'string' }, runs: { type: 'string', default: '3' }, timeout: { type: 'string', default: '60000' } },
    allowPositionals: true,
  });
  const runs = Number(values.runs);
  const timeoutMs = Number(values.timeout);
  if (!values.command || !values.cwd || !values.output || !Number.isInteger(runs) || runs < 1 || !Number.isFinite(timeoutMs) || timeoutMs <= 0) {
    throw new Error('Usage: node measure-startup.mjs --command executable --cwd directory --output report.json [--runs 3] [--timeout 60000] -- arguments');
  }
  const samples = [];
  for (let index = 0; index < runs; index++) {
    const sample = await measureStartup({ command: values.command, args: positionals, cwd: values.cwd, timeoutMs });
    samples.push(sample);
    process.stderr.write(`Run ${index + 1}: first output ${sample.firstOutputMs.toFixed(0)} ms, HTTP ready ${sample.httpReadyMs.toFixed(0)} ms\n`);
  }
  const sorted = samples.map(sample => sample.httpReadyMs).sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  const medianHttpReadyMs = sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
  await writeFile(values.output, JSON.stringify({ command: values.command, args: positionals, cwd: values.cwd, measuredAt: new Date().toISOString(), platform: process.platform, architecture: process.arch, medianHttpReadyMs, samples }, null, 2));
}
