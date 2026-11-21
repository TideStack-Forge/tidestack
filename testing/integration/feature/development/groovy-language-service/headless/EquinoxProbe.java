import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ServiceLoader;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.FrameworkFactory;

/** Scenario bootstrap; the analyzer itself runs inside its own OSGi bundle. */
public final class EquinoxProbe {
  public static void main(String[] args) throws Exception {
    Path run = Path.of(args[0]);
    Files.createDirectories(run);
    var options = new HashMap<String, String>();
    options.put(Constants.FRAMEWORK_STORAGE, run.resolve("configuration").toString());
    options.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
    options.put("osgi.instance.area", run.resolve("workspace").toUri().toString());
    options.put("eclipse.ignoreApp", "true");
    options.put("osgi.noShutdown", "true");
    options.put("eclipse.consoleLog", "true");
    var framework = ServiceLoader.load(FrameworkFactory.class).findFirst().orElseThrow().newFramework(options);
    framework.init();
    try {
      var bundles = new ArrayList<Bundle>();
      for (String line : Files.readAllLines(Path.of(args[1]))) {
        bundles.add(framework.getBundleContext().installBundle(Path.of(line).toUri().toString()));
      }
      if (!framework.adapt(org.osgi.framework.wiring.FrameworkWiring.class).resolveBundles(bundles)) {
        throw new IllegalStateException("The qualification bundle train could not resolve completely");
      }
      framework.start();
      for (String name : new String[] {"org.apache.felix.scr", "org.eclipse.equinox.common", "org.eclipse.equinox.registry",
          "org.eclipse.equinox.preferences", "org.eclipse.core.runtime", "org.eclipse.core.resources",
          "org.eclipse.jdt.core", "org.eclipse.jdt.groovy.core", "org.codehaus.groovy.eclipse.core"}) {
        var bundle = bundles.stream().filter(b -> name.equals(b.getSymbolicName())).findFirst().orElseThrow();
        System.out.println("START " + name + " " + bundle.getVersion());
        bundle.start();
      }
      var probe = bundles.stream().filter(b -> "com.ouroboros.testing.groovy.semantic".equals(b.getSymbolicName())).findFirst().orElseThrow();
      probe.start();
      probe.loadClass("com.ouroboros.testing.groovy.HeadlessSemantics").getMethod("run", String.class, String.class)
          .invoke(null, args[2], args[3]);
    } finally {
      framework.stop();
      framework.waitForStop(15_000);
    }
  }
}
