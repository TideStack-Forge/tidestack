package com.ouroboros.testing.groovy;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.jdt.groovy.model.GroovyCompilationUnit;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.groovy.search.ITypeRequestor.VisitStatus;
import org.eclipse.jdt.groovy.search.TypeInferencingVisitorFactory;

/** Real JDT workspace + official GE visitor. Input script bodies are never executed. */
public final class HeadlessSemantics {
  private static final String CONTEXT_SOURCE = "def local = $0.customers\ndef customer = local.get(0)\ncustomer.address.city.toUpperCase()\ndef names = local.collect { it.name }\nnames.get(0).toUpperCase()\n$0.size()\n$0.profile.label";

  public static void run(String fixtureJar, String output) throws Exception {
    var report = new JsonObject();
    report.addProperty("groovyVersion", groovy.lang.GroovySystem.getVersion());
    report.addProperty("javaVersion", System.getProperty("java.version"));
    var extensions = new JsonArray();
    for (var extension : Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.jdt.groovy.core.typeLookup")) {
      extensions.add(extension.getAttribute("lookup"));
    }
    report.add("typeLookupExtensions", extensions);
    var workspace = ResourcesPlugin.getWorkspace();
    var description = workspace.getDescription();
    description.setAutoBuilding(false);
    workspace.setDescription(description);
    var project = workspace.getRoot().getProject("semantic-qualification");
    project.create(null);
    project.open(null);
    var sourceFolder = project.getFolder("src");
    sourceFolder.create(true, true, null);
    var bin = project.getFolder("bin");
    bin.create(true, true, null);
    var projectDescription = project.getDescription();
    projectDescription.setNatureIds(new String[] {JavaCore.NATURE_ID, "org.eclipse.jdt.groovy.core.groovyNature"});
    project.setDescription(projectDescription, null);
    var java = JavaCore.create(project);
    java.setOutputLocation(bin.getFullPath(), null);
    java.setRawClasspath(new org.eclipse.jdt.core.IClasspathEntry[] {
        JavaCore.newSourceEntry(sourceFolder.getFullPath()),
        JavaCore.newLibraryEntry(new org.eclipse.core.runtime.Path(System.getProperty("java.home") + "/lib/jrt-fs.jar"), null, null),
        JavaCore.newLibraryEntry(new org.eclipse.core.runtime.Path(fixtureJar), null, null),
        JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("GROOVY_SUPPORT"))}, null);
    var options = java.getOptions(false);
    JavaCore.setComplianceOptions("21", options);
    java.setOptions(options);
    var pkg = java.getPackageFragmentRoot(sourceFolder).createPackageFragment("", true, null);
    var cases = new LinkedHashMap<String, String>();
    cases.put("generic", "import probe.Customer\nList<Customer> cs = []\ndef item = cs.get(0)\nitem.name");
    cases.put("implicit-it", "import probe.Customer\nList<Customer> cs = []\ncs.findAll { it.active }");
    cases.put("collect", "import probe.Customer\nList<Customer> cs = []\ndef names = cs.collect { it.name }\nnames.get(0).toUpperCase()");
    cases.put("delegate", "import probe.CustomerApi\nCustomerApi.withBuilder { appendTitle('x'); delegate.title; owner; thisObject }");
    cases.put("java-generic", "import probe.CustomerApi\ndef city = CustomerApi.load().get(0).address.city\ncity.toUpperCase()");
    cases.put("signature", "String text = 'abc'\ntext.substring(0, 1)");
    cases.put("safe-spread", "import probe.Customer\nList<Customer> cs = []\ndef names = cs*.name\ndef label = \"${cs[0]?.name}\"\nnames.get(0).toUpperCase()\nlabel.toUpperCase()");
    cases.put("symbols", "def value = 'x'\ndef other(value) { value }\nvalue.toUpperCase()");
    cases.put("incomplete", "String text = 'x'\ntext.");
    cases.put("context", CONTEXT_SOURCE);
    cases.put("context-java", "def customers = javaContext.customers\ndef names = customers.collect { it.name }\nnames.get(0).toUpperCase()");
    var results = new JsonObject();
    Path contextDirectory = Path.of(output).getParent().getParent().getParent().resolve("platform");
    for (var test : cases.entrySet()) {
      var unit = pkg.createCompilationUnit("Probe_" + test.getKey().replace('-', '_') + ".groovy", test.getValue(), true, null);
      if (!(unit instanceof GroovyCompilationUnit groovy)) throw new IllegalStateException("Patched JDT did not recognize Groovy source");
      groovy.becomeWorkingCopy(null);
      try {
        if (test.getKey().startsWith("context")) unit.getResource().setPersistentProperty(ContextTypeLookup.CONTEXT_FILE,
            contextDirectory.resolve("context-string.json").toString());
        results.add(test.getKey(), observe(groovy, test.getValue()));
        if (test.getKey().equals("symbols")) select(groovy, test.getValue(), results.getAsJsonObject(test.getKey()));
        if (test.getKey().equals("incomplete")) complete(groovy, test.getValue(), results.getAsJsonObject(test.getKey()));
        if (test.getKey().equals("context")) {
          unit.getResource().setPersistentProperty(ContextTypeLookup.CONTEXT_FILE, contextDirectory.resolve("context-integer.json").toString());
          results.add("context-updated", observe(groovy, test.getValue()));
          unit.getResource().setPersistentProperty(ContextTypeLookup.CONTEXT_FILE, null);
          results.add("context-removed", observe(groovy, test.getValue()));
          unit.getResource().setPersistentProperty(ContextTypeLookup.CONTEXT_FILE, contextDirectory.resolve("context-string.json").toString());
          results.add("context-restored", observe(groovy, test.getValue()));
        }
      } finally {
        groovy.discardWorkingCopy();
        unit.delete(true, null);
      }
    }
    report.add("documents", results);
    report.addProperty("jvmUptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
    report.addProperty("heapUsedBytes", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
    report.addProperty("heapCommittedBytes", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getCommitted());
    workspace.save(true, null);
    Files.writeString(Path.of(output), new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
  }

  private static JsonObject observe(GroovyCompilationUnit unit, String source) {
    long started = System.nanoTime();
    unit.getNewModuleInfo();
    var report = new JsonObject();
    report.addProperty("source", source);
    var observations = new JsonArray();
    new TypeInferencingVisitorFactory().createVisitor(unit).visitCompilationUnit((node, result, enclosing) -> {
      if (node.getStart() >= 0 && node.getEnd() > node.getStart()) {
        var observation = new JsonObject();
        observation.addProperty("start", node.getStart());
        observation.addProperty("end", node.getEnd());
        observation.addProperty("text", source.substring(Math.min(node.getStart(), source.length()), Math.min(node.getEnd(), source.length())));
        observation.addProperty("type", result.type == null ? "<none>" : result.type.toString(false));
        observation.addProperty("confidence", result.confidence.name());
        if (result.declaration != null) {
          observation.addProperty("declarationStart", result.declaration.getStart());
          observation.addProperty("declarationEnd", result.declaration.getEnd());
          observation.addProperty("declarationKind", result.declaration.getClass().getSimpleName());
        }
        if (result.declaration instanceof MethodNode method) {
          observation.addProperty("method", method.getName());
          var parameters = new JsonArray();
          for (var parameter : method.getParameters()) parameters.add(parameter.getType().toString(false));
          observation.add("parameters", parameters);
        }
        observations.add(observation);
      }
      return VisitStatus.CONTINUE;
    });
    report.add("types", observations);
    var diagnostics = new JsonArray();
    var module = unit.getModuleInfo(true);
    if (module != null && module.result != null && module.result.getAllProblems() != null) {
      for (var problem : module.result.getAllProblems()) {
        var diagnostic = new JsonObject();
        diagnostic.addProperty("message", problem.getMessage());
        diagnostic.addProperty("error", problem.isError());
        diagnostic.addProperty("start", problem.getSourceStart());
        diagnostic.addProperty("end", problem.getSourceEnd());
        diagnostics.add(diagnostic);
      }
    }
    report.add("diagnostics", diagnostics);
    report.addProperty("durationMs", (System.nanoTime() - started) / 1_000_000.0);
    return report;
  }

  private static void select(GroovyCompilationUnit unit, String source, JsonObject report) {
    var selections = new JsonArray();
    try {
      for (var element : unit.codeSelect(source.lastIndexOf("value"), "value".length())) {
        var selection = new JsonObject();
        selection.addProperty("name", element.getElementName());
        selection.addProperty("kind", element.getElementType());
        if (element instanceof ISourceReference reference) {
          selection.addProperty("start", reference.getNameRange().getOffset());
          selection.addProperty("length", reference.getNameRange().getLength());
        }
        selections.add(selection);
      }
    } catch (JavaModelException | RuntimeException | LinkageError e) {
      report.addProperty("selectionError", e.toString());
    }
    report.add("selections", selections);
  }

  private static void complete(GroovyCompilationUnit unit, String source, JsonObject report) {
    var completions = new JsonArray();
    try {
      unit.codeComplete(source.length(), new CompletionRequestor() {
        @Override public void accept(CompletionProposal proposal) {
          completions.add(new String(proposal.getCompletion()));
        }
      });
    } catch (JavaModelException | RuntimeException | LinkageError e) {
      report.addProperty("completionError", e.toString());
    }
    report.add("completions", completions);
  }
}
