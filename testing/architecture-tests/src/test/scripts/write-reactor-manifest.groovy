import java.nio.file.Files

// Use Maven's resolved projects so profiles and project selection keep Maven's semantics.
def repository = session.allProjects.find {
    new File(it.basedir, 'testing/architecture-tests/pom.xml').isFile()
}
if (repository == null) {
    throw new IllegalStateException(
        'Run from the repository root: mvn -pl testing/architecture-tests -am test')
}
def root = repository.basedir.toPath().toAbsolutePath().normalize()
def rows = session.allProjects.findAll {
    it != project && it.packaging != 'pom'
}.collect { module ->
    def sourceDirectories = module.compileSourceRoots.collect { new File(it).toPath() }
    boolean hasCode = sourceDirectories.any { directory ->
        if (!Files.isDirectory(directory)) {
            return false
        }
        Files.walk(directory).withCloseable { paths ->
            paths.anyMatch { Files.isRegularFile(it) &&
                (it.toString().endsWith('.java') || it.toString().endsWith('.groovy')) }
        }
    }
    boolean hasResources = module.resources.any { Files.isDirectory(new File(it.directory).toPath()) }
    if (!hasCode && !hasResources) {
        return null
    }
    def modulePath = root.relativize(module.basedir.toPath().toAbsolutePath().normalize())
    def output = root.relativize(new File(module.build.outputDirectory).toPath().toAbsolutePath().normalize())
    [modulePath.toString().replace('\\', '/'), output.toString().replace('\\', '/'), hasCode].join('\t')
}.findAll { it != null }.sort()
if (rows.isEmpty()) {
    throw new IllegalStateException('The architecture reactor contains no production modules')
}
def manifest = new File(project.build.directory, 'architecture-reactor.tsv').toPath()
Files.createDirectories(manifest.parent)
Files.writeString(manifest, "repository\t${root}\n" + rows.join('\n') + '\n')
log.info("Architecture scope: ${rows.size()} production modules from Maven reactor")

// Keep Maven scopes and inherited/profile dependencies from the effective reactor models.
def dependencyRows = session.allProjects.collectMany { module ->
    def source = module.artifactId
    module.dependencies.findAll { it.groupId == 'com.ouroboros' }.collect { dependency ->
        [source, dependency.artifactId, dependency.scope ?: 'compile'].join('\t')
    }
}.sort()
Files.writeString(new File(project.build.directory, 'architecture-maven-dependencies.tsv').toPath(),
    "repository\t${root}\n" + dependencyRows.join('\n') + '\n')

// Resolve each effective project's production dependencies through Maven. This also
// works for a standalone architecture run after the production reactor is compiled;
// unrelated versions in the local repository cannot satisfy a service contract.
def resolver = session.container.lookup(org.apache.maven.project.ProjectDependenciesResolver)
def runtimeClasspath = session.allProjects.collectMany { module ->
    def request = new org.apache.maven.project.DefaultDependencyResolutionRequest(module, session.repositorySession)
    request.setResolutionFilter(new org.eclipse.aether.util.filter.ScopeDependencyFilter('test'))
    def result = resolver.resolve(request)
    result.resolvedDependencies.findAll { dependency ->
        dependency.scope != 'test' && dependency.artifact.groupId != 'com.ouroboros'
            && dependency.artifact.file != null
    }.collect { it.artifact.file.toPath().toAbsolutePath().normalize().toString() }
}.unique().sort()
Files.writeString(new File(project.build.directory, 'architecture-runtime-classpath.txt').toPath(),
    runtimeClasspath.join('\n') + '\n')
