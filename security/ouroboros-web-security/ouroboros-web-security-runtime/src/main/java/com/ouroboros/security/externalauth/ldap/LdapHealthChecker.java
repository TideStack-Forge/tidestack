package com.ouroboros.security.externalauth.ldap;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.security.externalauth.ProviderHealthChecker;
import com.ouroboros.security.externalauth.config.LdapProviderSpec;

/**
 * Health checker for LDAP providers. Tests connectivity by opening an anonymous LDAP connection.
 */
public class LdapHealthChecker implements ProviderHealthChecker {

    private static final Logger logger = LoggerFactory.getLogger(LdapHealthChecker.class);

    private final LdapProviderSpec spec;

    public LdapHealthChecker(LdapProviderSpec spec) {
        this.spec = spec;
    }

    @Override
    public boolean isHealthy() {
        DirContext ctx = null;
        try {
            Hashtable<String, String> env = new Hashtable<String, String>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL, spec.getUrls().get(0));
            env.put(Context.SECURITY_AUTHENTICATION, "none");
            env.put("com.sun.jndi.ldap.connect.timeout", "5000");
            env.put("com.sun.jndi.ldap.read.timeout", "5000");
            ctx = new InitialDirContext(env);
            return true;
        } catch (Exception e) {
            logger.debug("LDAP health check failed for provider {}: {}", spec.getKey(), e.getMessage());
            return false;
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (Exception e) {
                    logger.warn("Failed to close LDAP context: {}", e.getMessage());
                }
            }
        }
    }
}
