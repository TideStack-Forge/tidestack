const config = require('../scripts/config.cjs');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const dist = path.resolve('frontends/web/main-app/dist');
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2', '.ico': 'image/x-icon' };
http.createServer((req, res) => {
  const pathname = new URL(req.url, 'http://127.0.0.1').pathname;
  if (pathname === '/rp/callback' || pathname === '/rp/signed-out') {
    res.writeHead(200, {'content-type':'text/html; charset=utf-8','cache-control':'no-store'});
    return res.end('<!doctype html><title>验收客户端</title><h1>' + (pathname.endsWith('callback') ? '测试应用收到授权回调' : '测试应用收到退出回调') + '</h1>');
  }
  if (/^\/(api(?:\/|$)|oauth2(?:\/|$)|\.well-known(?:\/|$)|connect(?:\/|$)|userinfo(?:\/|$))/.test(pathname)) {
    const backend = http.request({hostname:'127.0.0.1', port:Number(process.env.SSO_BACKEND_PORT || 18371), path:req.url, method:req.method, headers:{...req.headers, 'x-forwarded-proto':'http','x-forwarded-host':req.headers.host}}, answer => {
      res.writeHead(answer.statusCode, answer.headers); answer.pipe(res);
    });
    backend.on('error', () => { if (!res.headersSent) res.writeHead(502); res.end('fixture backend unavailable'); });
    req.pipe(backend);
    return;
  }
  const file = path.resolve(dist, '.' + pathname);
  if (!file.startsWith(dist + path.sep) && file !== dist) { res.writeHead(404); return res.end(); }
  const chosen = fs.existsSync(file) && fs.statSync(file).isFile() ? file : path.extname(pathname) ? null : path.join(dist, 'index.html');
  if (!chosen) { res.writeHead(404); return res.end(); }
  res.writeHead(200, {'content-type':mime[path.extname(chosen)] || 'application/octet-stream'});
  fs.createReadStream(chosen).pipe(res);
}).listen(Number(process.env.SSO_FRONTEND_PORT || 18372), '127.0.0.1');
