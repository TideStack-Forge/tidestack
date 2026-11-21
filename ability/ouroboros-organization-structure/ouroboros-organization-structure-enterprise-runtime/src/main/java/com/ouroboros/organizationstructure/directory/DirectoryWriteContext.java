package com.ouroboros.organizationstructure.directory;

/** Only the confirmed synchronization command enters this scope. It is never a request option. */
final class DirectoryWriteContext {
  private static final ThreadLocal<String> SCOPE = new ThreadLocal<>();
  private DirectoryWriteContext() { }
  static boolean owns(String scopeId) { return scopeId != null && scopeId.equals(SCOPE.get()); }
  static void run(String scopeId, Runnable command) {
    String previous = SCOPE.get(); SCOPE.set(scopeId);
    try { command.run(); }
    finally { if (previous == null) { SCOPE.remove(); } else { SCOPE.set(previous); } }
  }
}
