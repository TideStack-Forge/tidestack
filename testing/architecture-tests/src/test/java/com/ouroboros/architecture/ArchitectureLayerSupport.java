package com.ouroboros.architecture;

import java.util.Set;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

/** Shared semantic layer model. Maven location disambiguates split package families. */
final class ArchitectureLayerSupport {
    private static final Set<String> AUTHORITY_PROJECTIONS = Set.of(
        "com.ouroboros.appmodule.model.AuthorityTreeNode",
        "com.ouroboros.appmodule.model.ArrangedModuleAuthority",
        "com.ouroboros.appmodule.model.PlacementModuleAuthority",
        "com.ouroboros.appmodule.model.AuthoritiesAppModule");

    private ArchitectureLayerSupport() {
    }

    static String layer(String module, String type) {
        if (AUTHORITY_PROJECTIONS.stream().anyMatch(name -> type.equals(name) || type.startsWith(name + "$"))) {
            return "Bridge";
        }
        if (module.startsWith("libs/")) {
            return "Foundation";
        }
        if (module.startsWith("platform/")) {
            return "Platform";
        }
        if (module.startsWith("dev-module/") || module.endsWith("-dev")) {
            return "Dev";
        }
        if (module.startsWith("app-module/")) {
            return "App";
        }
        if (module.startsWith("security/") || type.startsWith("com.ouroboros.security.")) {
            return "Security";
        }
        if (module.startsWith("ability/ouroboros-approval-workflow-external-integration/")
            && !module.endsWith("-core")) {
            return "App";
        }
        if (module.startsWith("ability/")
            || module.endsWith("ouroboros-statistics-reporting-runtime")
            || module.endsWith("ouroboros-business-model-runtime")
            || module.endsWith("ouroboros-business-model-default")
            || module.endsWith("ouroboros-logicflow-debug-helper")) {
            return "Ability";
        }
        if (Set.of(
            "core/ouroboros-app-module-model/ouroboros-app-module-model-core",
            "core/ouroboros-app-module-model/ouroboros-app-module-model-runtime",
            "core/ouroboros-app-module-model/ouroboros-app-module-model-procode").contains(module)) {
            return "Bridge";
        }
        if (module.endsWith("ouroboros-business-model-security-core")) {
            return "Security";
        }
        if (module.endsWith("ouroboros-business-model-util")) {
            return "Security";
        }
        if (module.endsWith("ouroboros-menu-model-runtime")
            || module.endsWith("ouroboros-ui-model-schema-runtime")) {
            return "Bridge";
        }
        if (module.startsWith("core/ouroboros-approval-workflow/")
            && !module.endsWith("-core")) {
            return "App";
        }
        if (type.equals("com.ouroboros.businessmodel.model.BusinessModelModule")
            || type.startsWith("com.ouroboros.businessmodel.model.BusinessModelModule$")) {
            return "Bridge";
        }
        if (module.startsWith("core/")) {
            return "Core";
        }
        throw new IllegalArgumentException("Unclassified architecture module: " + module + " (" + type + ")");
    }

    static ArchRule rule(ArchitectureReactor reactor) {
        var layers = Architectures.layeredArchitecture().consideringOnlyDependenciesInLayers();
        for (String name : Set.of("Foundation", "Core", "Bridge", "Security", "Ability", "App", "Dev", "Platform")) {
            layers = layers.layer(name).definedBy(DescribedPredicate.describe(name, type ->
                reactor.moduleOf(type).map(module -> layer(module, type.getName()).equals(name)).orElse(false)));
        }
        return layers
            .whereLayer("Foundation").mayNotAccessAnyLayer()
            .whereLayer("Core").mayOnlyAccessLayers("Foundation")
            .whereLayer("Bridge").mayOnlyAccessLayers("Foundation", "Core", "Security")
            .whereLayer("Security").mayOnlyAccessLayers("Foundation", "Core", "Bridge")
            .whereLayer("Ability").mayOnlyAccessLayers("Foundation", "Core", "Bridge", "Security")
            .whereLayer("App").mayOnlyAccessLayers("Foundation", "Core", "Bridge", "Security", "Ability")
            .whereLayer("Dev").mayOnlyAccessLayers("Foundation", "Core", "Bridge", "Security", "Ability", "App")
            .ensureAllClassesAreContainedInArchitecture()
            .as("[P0-3] Module roles and accepted projection boundaries");
    }
}
