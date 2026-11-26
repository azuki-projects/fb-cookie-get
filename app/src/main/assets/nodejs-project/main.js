'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const { Worker } = require('worker_threads');

const projectsRoot = path.resolve(process.argv[2]);
const port = Number(process.argv[3] || 38271);
const logs = [];
let activeWorker = null;
let runId = 0;

function addLog(message) {
  logs.push(String(message));
  if (logs.length > 600) logs.shift();
  console.log(String(message));
}
function send(res, status, data) {
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': 'http://127.0.0.1'
  });
  res.end(JSON.stringify(data));
}
function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.on('data', chunk => {
      raw += chunk;
      if (raw.length > 1024 * 1024) reject(new Error('Request is too large'));
    });
    req.on('end', () => {
      try { resolve(JSON.parse(raw || '{}')); }
      catch (error) { reject(error); }
    });
    req.on('error', reject);
  });
}
function stopActive() {
  if (!activeWorker) return Promise.resolve(false);
  const old = activeWorker;
  activeWorker = null;
  return old.terminate().then(() => {
    addLog('[runner] Script stopped');
    return true;
  });
}
function startFile(file, argsText) {
  const absolute = path.resolve(file);
  if (!absolute.startsWith(projectsRoot + path.sep)) {
    throw new Error('Selected file is outside the projects directory');
  }
  if (!fs.existsSync(absolute) || !fs.statSync(absolute).isFile()) {
    throw new Error('File does not exist');
  }
  return stopActive().then(() => new Promise(resolve => {
    const thisRun = ++runId;
    const workerSource = `
      const { parentPort, workerData } = require('worker_threads');
      const Module = require('module');
      const fs = require('fs');
      const path = require('path');
      const file = workerData.file;
      process.argv = [process.execPath, file].concat(workerData.args || []);
      const out = (level, args) => {
        const line = args.map(v => {
          if (typeof v === 'string') return v;
          try { return JSON.stringify(v); } catch (_) { return String(v); }
        }).join(' ');
        parentPort.postMessage({ type: 'log', line: level ? '[' + level + '] ' + line : line });
      };
      global.console = {
        log: (...a) => out('', a),
        info: (...a) => out('INFO', a),
        warn: (...a) => out('WARN', a),
        error: (...a) => out('ERROR', a),
        debug: (...a) => out('DEBUG', a)
      };
      try {
        const mod = new Module(file);
        mod.filename = file;
        mod.paths = Module._nodeModulePaths(path.dirname(file));
        mod._compile(fs.readFileSync(file, 'utf8'), file);
        parentPort.postMessage({ type: 'loaded' });
      } catch (error) {
        parentPort.postMessage({ type: 'error', line: error && error.stack ? error.stack : String(error) });
      }
    `;
    const parsedArgs = String(argsText || '').trim().split(/\\s+/).filter(Boolean);\n    const worker = new Worker(workerSource, { eval: true, workerData: { file: absolute, args: parsedArgs } });
    activeWorker = worker;
    worker.on('message', message => {
      if (thisRun !== runId) return;
      if (message.type === 'log') addLog(message.line);
      else if (message.type === 'error') addLog('[ERROR] ' + message.line);
      else if (message.type === 'loaded') addLog('[runner] ' + path.basename(absolute) + ' loaded');
    });
    worker.on('error', error => {
      if (activeWorker === worker) activeWorker = null;
      addLog('[WORKER ERROR] ' + (error.stack || error.message));
    });
    worker.on('exit', code => {
      if (activeWorker === worker) activeWorker = null;
      if (code !== 0) addLog('[runner] Worker exited with code ' + code);
    });
    resolve({ ok: true, file: absolute });
  }));
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === 'GET' && req.url === '/health') {
      return send(res, 200, { ok: true, version: process.version, running: !!activeWorker });
    }
    if (req.url === '/logs' && (req.method === 'GET' || req.method === 'POST')) {
      if (req.method === 'POST') await readBody(req);
      return send(res, 200, { logs, running: !!activeWorker });
    }
    if (req.method !== 'POST') return send(res, 405, { error: 'POST required' });
    const data = await readBody(req);
    if (req.url === '/run') {
      const result = await startFile(data.file || '', data.args || '');
      addLog('[runner] Started ' + path.basename(result.file));
      return send(res, 200, result);
    }
    if (req.url === '/stop') {
      const stopped = await stopActive();
      return send(res, 200, { ok: true, stopped });
    }
    if (req.url === '/clear') {
      logs.length = 0;
      return send(res, 200, { ok: true });
    }
    return send(res, 404, { error: 'Unknown endpoint' });
  } catch (error) {
    addLog('[ERROR] ' + (error.stack || error.message));
    return send(res, 400, { error: error.message });
  }
});

server.listen(port, '127.0.0.1', () => addLog('[system] Node.js ' + process.version + ' ready on 127.0.0.1:' + port));
