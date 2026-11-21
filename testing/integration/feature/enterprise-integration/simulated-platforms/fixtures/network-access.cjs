const net = require('node:net');
// Fixed ingress ports only; the application and provider network have no Internet route.
const targets = { 8081: ['node-a', 8080], 8082: ['node-b', 8080], 8083: ['simulation-providers', 8080], 8443: ['simulation-providers', 443] };
const servers = Object.entries(targets).map(([port, [host, targetPort]]) => net.createServer(incoming => {
  const upstream = net.connect(targetPort, host);
  incoming.on('error', () => upstream.destroy());
  upstream.on('error', () => incoming.destroy());
  incoming.pipe(upstream); upstream.pipe(incoming);
}).listen(Number(port), '0.0.0.0'));
process.on('SIGTERM', () => { for (const server of servers) server.close(); process.exit(0); });
