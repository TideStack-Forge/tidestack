package com.ouroboros.dev.groovy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.google.gson.JsonParser;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.jdt.groovy.model.GroovyCompilationUnit;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/** Owns a JDT project for the helper's trusted, immutable application classpath. */
final class GroovyWorkspace implements AutoCloseable {
  private final IPackageFragment source;
  private final Map<String, GroovyDocument> documents = new LinkedHashMap<>();

  GroovyWorkspace(String classpathFile) throws Exception {
    var workspace = ResourcesPlugin.getWorkspace();
    var description = workspace.getDescription();
    description.setAutoBuilding(false);
    workspace.setDescription(description);
    var project = workspace.getRoot().getProject("application");
    if (!project.exists()) project.create(null);
    project.open(null);
    var folder = project.getFolder("src");
    if (!folder.exists()) folder.create(true, true, null);
    var bin = project.getFolder("bin");
    if (!bin.exists()) bin.create(true, true, null);
    var projectDescription = project.getDescription();
    projectDescription.setNatureIds(new String[] {JavaCore.NATURE_ID, "org.eclipse.jdt.groovy.core.groovyNature"});
    project.setDescription(projectDescription, null);
    var javaProject = JavaCore.create(project);
    javaProject.setOutputLocation(bin.getFullPath(), null);
    var entries = new ArrayList<org.eclipse.jdt.core.IClasspathEntry>();
    entries.add(JavaCore.newSourceEntry(folder.getFullPath()));
    entries.add(JavaCore.newLibraryEntry(new org.eclipse.core.runtime.Path(System.getProperty("java.home") + "/lib/jrt-fs.jar"), null, null));
    for (var entry : JsonParser.parseString(Files.readString(Path.of(classpathFile))).getAsJsonArray()) {
      Path path = Path.of(entry.getAsString()).toRealPath();
      entries.add(JavaCore.newLibraryEntry(new org.eclipse.core.runtime.Path(path.toString()), null, null));
    }
    entries.add(JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("GROOVY_SUPPORT")));
    javaProject.setRawClasspath(entries.toArray(org.eclipse.jdt.core.IClasspathEntry[]::new), null);
    var options = javaProject.getOptions(false);
    JavaCore.setComplianceOptions("21", options);
    javaProject.setOptions(options);
    source = javaProject.getPackageFragmentRoot(folder).createPackageFragment("", true, null);
  }

  GroovyDocument document(String uri) {
    var document = documents.get(uri);
    if (document == null) throw new IllegalArgumentException("Document is not open");
    return document;
  }

  void open(TextDocumentItem item) throws Exception {
    if (documents.containsKey(item.getUri())) throw new IllegalArgumentException("Document is already open");
    if (!"groovy".equals(item.getLanguageId()) || item.getText().length() > 1_000_000 || documents.size() >= 64)
      throw new IllegalArgumentException("Invalid Groovy document or workspace capacity exceeded");
    String name = "Script_" + java.util.UUID.randomUUID().toString().replace("-", "") + ".groovy";
    var unit = source.createCompilationUnit(name, GroovySyntax.forEditor(item.getText()), true, null);
    if (!(unit instanceof GroovyCompilationUnit groovy)) throw new IllegalStateException("Patched JDT is unavailable");
    documents.put(item.getUri(), new GroovyDocument(item.getUri(), groovy, item.getText(), item.getVersion()));
  }

  void change(DidChangeTextDocumentParams params) throws Exception {
    var document = document(params.getTextDocument().getUri());
    if (params.getTextDocument().getVersion() <= document.version) return;
    String text = document.source;
    for (var change : params.getContentChanges()) {
      if (change.getRange() == null) text = change.getText();
      else {
        document.source = text;
        int start = document.offset(change.getRange().getStart()), end = document.offset(change.getRange().getEnd());
        text = text.substring(0, start) + change.getText() + text.substring(end);
      }
    }
    document.change(text, params.getTextDocument().getVersion());
  }

  void close(String uri) throws Exception {
    var document = documents.remove(uri);
    if (document != null) document.close();
  }

  Hover hover(TextDocumentPositionParams params) {
    var document = document(params.getTextDocument().getUri());
    var occurrence = document.at(params.getPosition());
    if (occurrence == null || occurrence.type() == null) return null;
    String detail = occurrence.declaration() instanceof MethodNode method ? GroovyCompletions.signature(method) : occurrence.type().toString(false);
    return new Hover(new MarkupContent("markdown", "```groovy\n" + detail + "\n```"), document.range(occurrence.node()));
  }

  List<Location> references(TextDocumentPositionParams params, boolean includeDeclaration) {
    var document = document(params.getTextDocument().getUri());
    var selected = document.at(params.getPosition());
    if (selected == null || selected.declaration() == null) return List.of();
    ASTNode declaration = selected.declaration();
    // Embedded scripts are independent compilation units, not a user filesystem workspace.
    if (declaration.getStart() < 0 || declaration.getEnd() <= declaration.getStart()) return List.of();
    var locations = new LinkedHashMap<String, Location>();
    for (var occurrence : document.analyze()) {
      if (occurrence.declaration() != declaration || !isName(occurrence.node())) continue;
      Range range = nameRange(document, occurrence.node());
      Range declarationRange = nameRange(document, declaration);
      if (includeDeclaration || !range.equals(declarationRange)) locations.put(range.toString(), new Location(document.uri, range));
    }
    if (includeDeclaration) {
      var range = nameRange(document, declaration);
      locations.put(range.toString(), new Location(document.uri, range));
    }
    return List.copyOf(locations.values());
  }

  List<Location> definition(TextDocumentPositionParams params) {
    var document = document(params.getTextDocument().getUri());
    var occurrence = document.at(params.getPosition());
    if (occurrence == null || occurrence.declaration() == null || occurrence.declaration().getStart() < 0) return List.of();
    return List.of(new Location(document.uri, nameRange(document, occurrence.declaration())));
  }

  WorkspaceEdit rename(RenameParams params) {
    if (params.getNewName() == null || !validName(params.getNewName()))
      throw new IllegalArgumentException("Invalid Groovy identifier");
    if (org.codehaus.groovy.syntax.Types.isKeyword(params.getNewName())) throw new IllegalArgumentException("Cannot rename to a keyword");
    var document = document(params.getTextDocument().getUri());
    var selected = document.at(params.getPosition());
    if (selected == null || !(selected.declaration() instanceof Variable)) throw new IllegalArgumentException("Rename supports script-local variables and parameters");
    List<TextEdit> edits = references(params, true).stream().map(l -> new TextEdit(l.getRange(), params.getNewName())).toList();
    var edit = new TextDocumentEdit(new VersionedTextDocumentIdentifier(document.uri, document.version), edits);
    return new WorkspaceEdit(List.of(Either.forLeft(edit)));
  }

  private static boolean validName(String name) {
    return !name.isEmpty() && Character.isJavaIdentifierStart(name.charAt(0)) && name.chars().allMatch(Character::isJavaIdentifierPart);
  }

  private static boolean isName(ASTNode node) {
    return node instanceof Variable || node instanceof org.codehaus.groovy.ast.expr.ConstantExpression;
  }

  static Range nameRange(GroovyDocument document, ASTNode node) {
    if (node instanceof AnnotatedNode annotated && annotated.getNameStart() >= 0 && annotated.getNameEnd() >= annotated.getNameStart())
      return document.range(annotated.getNameStart(), annotated.getNameEnd() + 1);
    return document.range(node);
  }

  SignatureHelp signature(SignatureHelpParams params) throws Exception {
    var document = document(params.getTextDocument().getUri());
    int offset = document.offset(params.getPosition());
    var call = document.analyze().stream().filter(o -> o.declaration() instanceof MethodNode && o.node().getStart() < offset)
        .max(java.util.Comparator.comparingInt(o -> o.node().getStart())).orElse(null);
    if (call == null) return new SignatureHelp(List.of(), 0, 0);
    MethodNode method = (MethodNode) call.declaration();
    var signatures = new ArrayList<SignatureInformation>();
    for (var overload : method.getDeclaringClass().getMethods(method.getName())) {
      var signature = new SignatureInformation(GroovyCompletions.signature(overload));
      signature.setParameters(java.util.Arrays.stream(overload.getParameters()).map(p -> new ParameterInformation(p.getName())).toList());
      signatures.add(signature);
    }
    return new SignatureHelp(signatures, 0, 0);
  }

  List<Either<SymbolInformation, DocumentSymbol>> symbols(DocumentSymbolParams params) {
    var document = document(params.getTextDocument().getUri());
    var symbols = new LinkedHashMap<String, Either<SymbolInformation, DocumentSymbol>>();
    for (var occurrence : document.analyze()) {
      if (!(occurrence.node() instanceof VariableExpression variable) || occurrence.declaration() != variable || variable.getStart() < 0) continue;
      Range range = document.range(variable);
      symbols.put(variable.getStart() + ":" + variable.getName(), Either.forRight(new DocumentSymbol(variable.getName(), SymbolKind.Variable, range, range)));
    }
    for (var method : document.unit.getModuleNode().getMethods()) if (method.getStart() >= 0) {
      symbols.put("method:" + method.getStart(), Either.forRight(new DocumentSymbol(method.getName(), SymbolKind.Method, document.range(method), nameRange(document, method))));
    }
    return List.copyOf(symbols.values());
  }

  @Override public void close() throws Exception {
    Exception failure = null;
    for (var document : documents.values()) {
      try { document.close(); } catch (Exception e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
    }
    documents.clear();
    if (failure != null) throw failure;
  }
}
