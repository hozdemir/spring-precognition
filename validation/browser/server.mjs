import { build } from 'esbuild';
import { createServer, request } from 'node:http';
import { readFile } from 'node:fs/promises';
import { startBackend } from '../client/backend.mjs';

const output = new URL('../../target/browser-assets/', import.meta.url);
await build({
  absWorkingDir: new URL('.', import.meta.url).pathname,
  entryPoints: ['react.mjs', 'vue.mjs'], outdir: output.pathname,
  bundle: true, format: 'esm', platform: 'browser', sourcemap: true,
  define: { 'process.env.NODE_ENV': '"development"', __VUE_OPTIONS_API__: 'true', __VUE_PROD_DEVTOOLS__: 'false' },
});
const backend = await startBackend(['--demo.cors-origin=http://127.0.0.1:4173']);
const server = createServer(async (incoming, outgoing) => {
  const path = new URL(incoming.url, 'http://127.0.0.1:4173').pathname;
  if (['/users', '/profiles', '/demo/counters'].includes(path)) {
    const proxy = request(new URL(path, backend.baseURL), {
      method: incoming.method, headers: incoming.headers,
    }, response => {
      outgoing.writeHead(response.statusCode, response.headers);
      response.pipe(outgoing);
    });
    proxy.on('error', error => { outgoing.writeHead(502); outgoing.end(error.message); });
    incoming.pipe(proxy);
    return;
  }
  if (path === '/react.html' || path === '/vue.html') {
    const framework = path.slice(1, -5);
    outgoing.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    outgoing.end(`<!doctype html><html lang="en"><meta charset="utf-8"><title>${framework} Precognition verification</title><div id="root"></div><script>window.PRECOGNITION_BASE_URL=${JSON.stringify(backend.baseURL)}</script><script type="module" src="/${framework}.js"></script></html>`);
    return;
  }
  if (['/react.js', '/vue.js', '/react.js.map', '/vue.js.map'].includes(path)) {
    try {
      outgoing.setHeader('Content-Type', path.endsWith('.map') ? 'application/json' : 'text/javascript');
      outgoing.end(await readFile(new URL(path.slice(1), output)));
    } catch { outgoing.writeHead(404); outgoing.end(); }
    return;
  }
  outgoing.writeHead(404); outgoing.end();
});
const shutdown = async () => { server.closeAllConnections(); server.close(); await backend.close(); };
process.once('SIGTERM', shutdown);
process.once('SIGINT', shutdown);
server.once('error', async error => { console.error(error); await shutdown(); process.exitCode = 1; });
server.listen(4173, '127.0.0.1');
