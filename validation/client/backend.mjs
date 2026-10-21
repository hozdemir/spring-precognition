import { spawn, execFileSync } from 'node:child_process';
import { createWriteStream, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

export const root = fileURLToPath(new URL('../../', import.meta.url));

// Shared by the Node HTTP suite and the real React/Vue browser suite.
export async function startBackend(extraArgs = []) {
  const report = process.env.PRECOGNITION_REPORT
    ?? 'examples/boot4/target/surefire-reports/TEST-dev.precognition.example.PrecognitionIntegrationTest.xml';
  const classpath = execFileSync('python3', ['-c',
    "import sys,xml.etree.ElementTree as E; r=E.parse(sys.argv[1]); print(next(p.attrib['value'] for p in r.findall('./properties/property') if p.attrib['name']=='java.class.path'))",
    report], { cwd: root, encoding: 'utf8' }).trim();
  mkdirSync(new URL('../../target/validation/', import.meta.url), { recursive: true });
  const log = createWriteStream(new URL('../../target/validation/client-http-server.log', import.meta.url));
  const server = spawn('java', ['-cp', classpath, 'dev.precognition.example.ExampleApplication',
    '--server.address=127.0.0.1', '--server.port=0', ...extraArgs], { cwd: root });
  server.stdout.pipe(log, { end: false });
  server.stderr.pipe(log, { end: false });
  const close = async () => {
    if (server.pid && server.exitCode === null && server.signalCode === null) {
      const exited = new Promise(resolve => server.once('exit', resolve));
      server.kill('SIGTERM');
      const timeout = setTimeout(() => server.kill('SIGKILL'), 5000);
      await exited;
      clearTimeout(timeout);
    }
    log.end();
  };
  try {
    const port = await new Promise((resolve, reject) => {
      let output = '';
      const timeout = setTimeout(() => reject(new Error('Tomcat startup timed out; see target/validation/client-http-server.log')), 25000);
      server.once('error', error => { clearTimeout(timeout); reject(error); });
      server.once('exit', code => {
        clearTimeout(timeout);
        reject(new Error(`Tomcat exited (${code}); see target/validation/client-http-server.log`));
      });
      server.stdout.on('data', chunk => {
        output = (output + chunk.toString()).slice(-32768);
        const match = output.match(/Tomcat started on port (\d+)/);
        if (match) { clearTimeout(timeout); resolve(Number(match[1])); }
      });
    });
    return { baseURL: `http://127.0.0.1:${port}`, close };
  } catch (error) {
    await close();
    throw error;
  }
}
