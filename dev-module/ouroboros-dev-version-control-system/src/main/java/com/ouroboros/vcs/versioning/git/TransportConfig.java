package com.ouroboros.vcs.versioning.git;

import java.io.File;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

// TODO: 需要重构
public class TransportConfig implements TransportConfigCallback {
  private final SshSessionFactory sshSession;

  public TransportConfig(String sshKeyPath) {
    sshSession = getSshSessionFactory(sshKeyPath);
  }

  private SshSessionFactory getSshSessionFactory(String sshKeyPath) {
    return new JschConfigSessionFactory() {
      @Override
      protected void configure(OpenSshConfig.Host host, Session session) {
        session.setConfig("StrictHostKeyChecking", "yes");
      }

      @Override
      protected JSch createDefaultJSch(FS fs) throws JSchException {
        JSch sch = super.createDefaultJSch(fs);
        File userHome = fs.userHome();
        if (userHome != null) {
          File knownHosts = new File(new File(userHome, ".ssh"), "known_hosts");
          if (knownHosts.isFile()) {
            sch.setKnownHosts(knownHosts.getAbsolutePath());
          }
        }
        sch.addIdentity(sshKeyPath); //添加私钥文件
        return sch;
      }
    };
  }

  @Override
  public void configure(Transport transport) {
    SshTransport sshTransport = (SshTransport) transport;
    sshTransport.setSshSessionFactory(sshSession);
  }
}
