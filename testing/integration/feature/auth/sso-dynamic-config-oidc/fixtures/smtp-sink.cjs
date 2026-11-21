const config = require('../scripts/config.cjs');
const net = require('node:net');
const fs = require('node:fs');
const file = config.mailbox;
net.createServer(socket => {
  let buffer='', message=[], mode='', auth='';
  const send = (text) => socket.write(text+'\r\n');
  send('220 acceptance.local ESMTP');
  socket.setEncoding('utf8');
  socket.on('data', chunk => {
    buffer += chunk;
    for (let end; (end=buffer.indexOf('\r\n'))>=0;) {
      const line=buffer.slice(0,end);
      buffer=buffer.slice(end+2);
      if (mode==='DATA') {
        if (line!=='.') { message.push(line.replace(/^\.\./,'.')); continue; }
        fs.writeFileSync(file,message.join('\r\n'),{mode:0o600});
        mode=''; message=[]; send('250 queued'); continue;
      }
      if (auth==='user') { auth='password'; send('334 UGFzc3dvcmQ6'); continue; }
      if (auth==='password') { auth=''; send('235 authenticated'); continue; }
      if (/^EHLO /i.test(line)) { socket.write('250-acceptance.local\r\n250 AUTH LOGIN PLAIN\r\n'); }
      else if (/^AUTH LOGIN /i.test(line)) { auth='password'; send('334 UGFzc3dvcmQ6'); }
      else if (/^AUTH LOGIN$/i.test(line)) { auth='user'; send('334 VXNlcm5hbWU6'); }
      else if (/^AUTH PLAIN /i.test(line)) send('235 authenticated');
      else if (/^(HELO |MAIL FROM:|RCPT TO:|RSET|NOOP)/i.test(line)) send('250 ok');
      else if (line==='DATA') { mode='DATA'; send('354 finish with .'); }
      else if (line==='QUIT') { send('221 bye'); socket.end(); }
      else send('502 unsupported');
    }
  });
}).listen(Number(process.env.SSO_SMTP_PORT || 11025), '127.0.0.1');
