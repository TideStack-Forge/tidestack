package com.ouroboros.resource.dev.inspector;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.resource.dev.model.ResourceModel;
import com.ouroboros.resource.util.ResourcePathUtils;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * VCS file inspector for application resources.
 * Provides metadata extension for resource files.
 */
@Component
public class ResourceInspector {

    private static final String MODEL_FILE_DIRECTORY = "resources";
    private static final String MODEL_FILE_EXTENSION = ".resource.json";

    private final BaseDevelopService<ResourceModel> resourceDevService;

    public ResourceInspector(BaseDevelopService<ResourceModel> resourceDevService) {
        this.resourceDevService = resourceDevService;
    }

    /**
     * Proxy implementation for SPI registration
     */
    private static BaseDevelopService<ResourceModel> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(ResourceInspector.class).resourceDevService;
  }

  public static class Proxy extends AbstractVCSFileInspector {

        @Override
        protected void postProcessFile(VCSFile file, FileDescription description) {
            String scopedPath = toScopedPath(file);
            String fileName = file.getFileName();

            if (fileName.endsWith(MODEL_FILE_EXTENSION)) {
                description.setName(scopedPath)
                        .setIcon("resource-meta")
                        .setType("resource-metadata")
                        .setTitle(scopedPath + " (metadata)");
            } else {
                description.setName(fileName)
                        .setIcon(getIconByFileName(fileName))
                        .setType("resource-file")
                        .setTitle(fileName);
            }

            if (currentService() != null) {
                var model = currentService().getOne(scopedPath).orElse(null);
                if (model != null) {
                    if (StringUtils.isNotBlank(model.getDisplayName())) {
                        description.setTitle(model.getDisplayName());
                    }
                    if (StringUtils.isNotBlank(model.getDescription())) {
                        description.setDescription(model.getDescription());
                    }
                    if (StringUtils.isNotBlank(model.getVersionTag())) {
                        description.setVersion(model.getVersionTag());
                    }
                }
            }
        }

        @Override
        protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
            dirs.forEach(dir -> {
                if (dir.getPath().equals(MODEL_FILE_DIRECTORY)) {
                    dir.setTitle("Resources");
                    dir.setDescription("Application default resources");
                }
                if (dir.getPath().equals(MODEL_FILE_DIRECTORY + "/frontend")) {
                    dir.setTitle("Frontend Resources");
                }
                if (dir.getPath().equals(MODEL_FILE_DIRECTORY + "/backend")) {
                    dir.setTitle("Backend Resources");
                }
            });
        }

        @Override
        public boolean support(VCSFile file) {
            return file.getPath().startsWith(MODEL_FILE_DIRECTORY + "/");
        }

        private String toScopedPath(VCSFile file) {
            String relativePath = file.getPath().substring(MODEL_FILE_DIRECTORY.length() + 1);
            if (relativePath.endsWith(MODEL_FILE_EXTENSION)) {
                return relativePath.substring(0, relativePath.length() - MODEL_FILE_EXTENSION.length());
            }
            return relativePath;
        }

        /**
         * Get icon name based on file extension
         */
        private String getIconByFileName(String fileName) {
            String extension = ResourcePathUtils.getFileExtension(fileName);
            if (StringUtils.isBlank(extension)) {
                return "file";
            }

            switch (extension.toLowerCase()) {
                case "json":
                    return "file-json";
                case "xml":
                    return "file-xml";
                case "txt":
                case "md":
                    return "file-text";
                case "jpg":
                case "jpeg":
                case "png":
                case "gif":
                case "svg":
                    return "file-image";
                case "doc":
                case "docx":
                    return "file-word";
                case "xls":
                case "xlsx":
                    return "file-excel";
                case "pdf":
                    return "file-pdf";
                case "zip":
                case "rar":
                case "7z":
                    return "file-archive";
                default:
                    return "file";
            }
        }
    }
}
