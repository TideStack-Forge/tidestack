package com.ouroboros.dev.groovy;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ServiceLoader;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.FrameworkFactory;

/** Keeps the patched JDT/Groovy compiler outside the application classloader. */
public final class GroovyLanguageServerMain {
  private GroovyLanguageServerMain() {}

  public static void main(String[] args) throws Exception {
    Path distribution = Path.of(GroovyLanguageServerMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
    Path workspace = Path.of(args[0]);
    PrintStream protocol = System.out;
    System.setOut(System.err);
    var options = new HashMap<String, String>();
    options.put(Constants.FRAMEWORK_STORAGE, workspace.resolve("configuration").toString());
    options.put("osgi.instance.area", workspace.resolve("workspace").toUri().toString());
    options.put("eclipse.ignoreApp", "true");
    options.put("osgi.noShutdown", "true");
    var framework = ServiceLoader.load(FrameworkFactory.class).findFirst().orElseThrow().newFramework(options);
    framework.init();
    try {
      var bundles = new ArrayList<Bundle>();
      for (String line : Files.readAllLines(distribution.resolve("bundles.txt"))) {
        bundles.add(framework.getBundleContext().installBundle(distribution.resolve(line).toUri().toString()));
      }
      var platform = new HashMap<String, String>();
      for (String property : new String[] {"osgi.os", "osgi.arch", "osgi.ws"}) {
        String value = framework.getBundleContext().getProperty(property);
        if (value != null) platform.put(property, value);
      }
      var applicable = new ArrayList<Bundle>();
      for (var bundle : bundles) {
        String filter = bundle.getHeaders().get("Eclipse-PlatformFilter");
        if (filter == null || org.osgi.framework.FrameworkUtil.createFilter(filter).matches(platform)) applicable.add(bundle);
      }
      if (!framework.adapt(org.osgi.framework.wiring.FrameworkWiring.class).resolveBundles(applicable)) {
        for (var bundle : applicable) if (bundle.getState() == Bundle.INSTALLED) System.err.println("Unresolved language bundle: " + bundle.getSymbolicName());
        throw new IllegalStateException("Groovy language server bundles could not resolve");
      }
      framework.start();
      for (String name : new String[] {"org.apache.felix.scr", "org.eclipse.equinox.common", "org.eclipse.equinox.registry",
          "org.eclipse.equinox.preferences", "org.eclipse.core.runtime", "org.eclipse.core.resources",
          "org.eclipse.jdt.core", "org.eclipse.jdt.groovy.core", "org.codehaus.groovy.eclipse.core"}) {
        bundles.stream().filter(b -> name.equals(b.getSymbolicName())).findFirst().orElseThrow().start();
      }
      var server = bundles.stream().filter(b -> "com.ouroboros.dev.groovy".equals(b.getSymbolicName())).findFirst().orElseThrow();
      server.start();
      server.loadClass("com.ouroboros.dev.groovy.GroovyLanguageServer").getMethod("run", InputStream.class, java.io.OutputStream.class, String.class)
          .invoke(null, System.in, protocol, args[1]);
    } finally {
      framework.stop();
      framework.waitForStop(15_000);
    }
  }
}
