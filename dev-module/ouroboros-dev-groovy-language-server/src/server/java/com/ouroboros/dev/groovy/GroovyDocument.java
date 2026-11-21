package com.ouroboros.dev.groovy;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.jdt.groovy.model.GroovyCompilationUnit;
import org.eclipse.jdt.groovy.search.ITypeRequestor.VisitStatus;
import org.eclipse.jdt.groovy.search.TypeInferencingVisitorFactory;
import org.eclipse.jdt.groovy.search.TypeLookupResult;
import org.eclipse.lsp4j.*;

/** One working copy, confined to the language server's model executor. */
final class GroovyDocument implements AutoCloseable {
  final String uri;
  final GroovyCompilationUnit unit;
  String source;
  int version;
  String context = "{}";
  private List<Occurrence> cached;

  record Occurrence(ASTNode node, ClassNode type, ASTNode declaration, String confidence) {}

  GroovyDocument(String uri, GroovyCompilationUnit unit, String source, int version) throws Exception {
    this.uri = uri;
    this.unit = unit;
    this.source = source;
    this.version = version;
    unit.becomeWorkingCopy(null);
  }

  void change(String source, int version) throws Exception {
    if (version <= this.version) throw new IllegalArgumentException("Document version must increase");
    if (source.length() > 1_000_000) throw new IllegalArgumentException("Script exceeds the language service size limit");
    this.source = source;
    this.version = version;
    invalidate();
  }

  void context(String context) throws Exception {
    if (!context.equals(this.context)) {
      this.context = context;
      unit.getResource().setSessionProperty(ContextTypeLookup.CONTEXT, context);
      invalidate();
    }
  }

  private void invalidate() throws Exception {
    cached = null;
    unit.getBuffer().setContents(GroovySyntax.forEditor(source));
    unit.reconcile(org.eclipse.jdt.core.ICompilationUnit.NO_AST, org.eclipse.jdt.core.ICompilationUnit.FORCE_PROBLEM_DETECTION, null, null);
    unit.getNewModuleInfo();
  }

  List<Occurrence> analyze() {
    if (cached == null) cached = visit();
    return cached;
  }

  List<Occurrence> visit() {
    var occurrences = new ArrayList<Occurrence>();
    new TypeInferencingVisitorFactory().createVisitor(unit).visitCompilationUnit((node, result, enclosing) -> {
      if (node.getStart() >= 0 && node.getEnd() > node.getStart()) {
        occurrences.add(new Occurrence(node, result.type, result.declaration, result.confidence.name()));
      }
      return Thread.currentThread().isInterrupted() ? VisitStatus.STOP_VISIT : VisitStatus.CONTINUE;
    });
    return List.copyOf(occurrences);
  }

  Occurrence at(Position position) {
    int offset = offset(position);
    return analyze().stream().filter(o -> o.node().getStart() <= offset && offset <= o.node().getEnd())
        .min(Comparator.comparingInt(o -> o.node().getLength())).orElse(null);
  }

  int offset(Position position) {
    if (position.getLine() < 0 || position.getCharacter() < 0) throw new IllegalArgumentException("Invalid position");
    int line = 0, offset = 0;
    while (line < position.getLine() && offset < source.length()) if (source.charAt(offset++) == '\n') line++;
    int end = source.indexOf('\n', offset);
    if (end < 0) end = source.length();
    return Math.min(end, offset + position.getCharacter());
  }

  Position position(int offset) {
    offset = Math.max(0, Math.min(offset, source.length()));
    int line = 0, start = 0;
    for (int i = 0; i < offset; i++) if (source.charAt(i) == '\n') { line++; start = i + 1; }
    return new Position(line, offset - start);
  }

  Range range(int start, int end) { return new Range(position(start), position(end)); }
  Range range(ASTNode node) { return range(node.getStart(), node.getEnd()); }

  List<Diagnostic> diagnostics() {
    analyze();
    var diagnostics = new ArrayList<Diagnostic>();
    var module = unit.getModuleInfo(true);
    if (module != null && module.result != null && module.result.getAllProblems() != null) {
      for (var problem : module.result.getAllProblems()) {
        var diagnostic = new Diagnostic(range(problem.getSourceStart(), problem.getSourceEnd() + 1), problem.getMessage());
        diagnostic.setSeverity(problem.isError() ? DiagnosticSeverity.Error : DiagnosticSeverity.Warning);
        diagnostic.setSource("groovy");
        diagnostic.setCode(Integer.toString(problem.getID()));
        diagnostics.add(diagnostic);
      }
    }
    return List.copyOf(diagnostics);
  }

  @Override public void close() throws Exception {
    unit.discardWorkingCopy();
    unit.delete(true, null);
    cached = null;
  }
}
