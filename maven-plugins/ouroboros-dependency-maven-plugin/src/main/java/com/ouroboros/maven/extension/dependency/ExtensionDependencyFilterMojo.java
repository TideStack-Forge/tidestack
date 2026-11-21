package com.ouroboros.maven.extension.dependency;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "filter", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class ExtensionDependencyFilterMojo extends AbstractMojo {
  @Parameter(property = "ouroboros.extension.manifestUrl", required = true)
  private String manifestUrl;

  @Parameter(defaultValue = "${project.build.directory}/lib",
      property = "ouroboros.extension.inputDirectory")
  private File inputDirectory;

  @Parameter(defaultValue = "${project.build.directory}/extension-lib",
      property = "ouroboros.extension.outputDirectory")
  private File outputDirectory;

  @Parameter(defaultValue = "${project.build.directory}/ouroboros-dependency-filter-report.json",
      property = "ouroboros.extension.reportFile")
  private File reportFile;

  @Parameter(defaultValue = "false",
      property = "ouroboros.extension.ignoreVersionDifferences")
  private boolean ignoreVersionDifferences;

  @Parameter(defaultValue = "true",
      property = "ouroboros.extension.cleanOutputDirectory")
  private boolean cleanOutputDirectory;

  @Override
  public void execute() throws MojoExecutionException {
    RuntimeManifest manifest;
    try {
      manifest = new RuntimeManifestLoader().load(manifestUrl);
      FilterResult result = new ExtensionDependencyFilter(getLog())
          .filter(inputDirectory, outputDirectory, manifest, ignoreVersionDifferences, cleanOutputDirectory);
      writeReport(result);
      getLog().info("Filtered extension dependencies to: " + outputDirectory.getAbsolutePath());
      getLog().info("Wrote dependency filter report to: " + reportFile.getAbsolutePath());
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to filter extension dependencies", e);
    }
  }

  private void writeReport(FilterResult result) throws IOException {
    File parent = reportFile.getParentFile();
    if (parent != null) {
      parent.mkdirs();
    }
    ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    mapper.writeValue(reportFile, new Report(manifestUrl, inputDirectory, outputDirectory,
        ignoreVersionDifferences, result));
  }

  public static final class Report {
    private final String manifestUrl;
    private final String inputDirectory;
    private final String outputDirectory;
    private final boolean ignoreVersionDifferences;
    private final FilterResult result;

    Report(String manifestUrl, File inputDirectory, File outputDirectory,
        boolean ignoreVersionDifferences, FilterResult result) {
      this.manifestUrl = manifestUrl;
      this.inputDirectory = inputDirectory.getAbsolutePath();
      this.outputDirectory = outputDirectory.getAbsolutePath();
      this.ignoreVersionDifferences = ignoreVersionDifferences;
      this.result = result;
    }

    public String getManifestUrl() {
      return manifestUrl;
    }

    public String getInputDirectory() {
      return inputDirectory;
    }

    public String getOutputDirectory() {
      return outputDirectory;
    }

    public boolean isIgnoreVersionDifferences() {
      return ignoreVersionDifferences;
    }

    public FilterResult getResult() {
      return result;
    }
  }
}
