export function observeProcess(child, readiness, timeoutMs) {
  let output = '';
  let errors = '';
  const diagnostics = () => `stdout:\n${output}\nstderr:\n${errors}`;
  const ready = new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      reject(new Error(`Process startup timed out\n${diagnostics()}`));
    }, timeoutMs);
    child.stdout.setEncoding('utf8').on('data', chunk => {
      output += chunk;
      const match = output.match(readiness);
      if (match) {
        clearTimeout(timer);
        resolve(match);
      }
    });
    child.stderr.setEncoding('utf8').on('data', chunk => { errors += chunk; });
    child.once('error', error => {
      clearTimeout(timer);
      reject(error);
    });
    child.once('close', (code, signal) => {
      clearTimeout(timer);
      reject(new Error(`Process exited with ${code ?? signal}\n${diagnostics()}`));
    });
  });
  return { ready, diagnostics };
}
