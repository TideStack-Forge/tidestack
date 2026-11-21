# Model Metadata Discovery Pipeline

本文维护元数据发现和刷新的当前机制。

## Purpose and Scope

The Model Metadata Discovery Pipeline is responsible for discovering, loading, indexing, and serving model metadata from multiple sources to both runtime and development environments. It enables the platform's low-code/pro-code hybrid approach by supporting:

- **Dynamic models**: JSON-defined models loaded from filesystem or JAR resources
- **Typed models**: Java entity classes discovered via annotations
- **Development-time models**: VCS-backed models with version control integration
- **Runtime models**: Indexed, immutable models optimized for fast access

## Actors and Components

### Discovery Sources

| Source Type | Implementation | Location | Priority |
|---|---|---|---|
| **JAR Resources** | `JarUIModelProvider`, `JarUISchemaProvider`, business / logicflow / menu / authority / RPC / field-type JAR providers | Normal model legacy loaders read their old typed subdirectories, while current loaders scan `META-INF/ouroboros/metadata/**` by typed suffix (`.business.json`, `.logicflow.json`, `.ui-model.json`, `.ui-schema.json`, etc.) without using subdirectories as type. Direct-upgrade metadata such as `.field-type.json`, `.flow-node.json`, `.dev-menu.json`, `.widget.json`, `.error.json`, `.data-init.json`, `.rpc.json`, `.rpc.auth.json`, `.notification.json`, and `.business-log.json` only use the new suffix path. | Low (classpath); current metadata applies after legacy for the same provider |
| **Filesystem (Low-code)** | `AppBusinessModelMetaProvider`, `AppLogicModelMetaProvider`, `LowcodeUIModelProvider`, `AppUiSchemaSchemaProvider`, workspace field-type / error-code providers | App home business models read `business-model/**/*.business.json`. Other normal models read current suffix files from their owning directories (`logic-flow/**/*.logicflow.json`, `ui-model/**/*.ui-model.json`, `ui-schema/**/*.ui-schema.json`) and keep legacy `*.json` reads where that provider owns compatibility. Direct-upgrade types such as `field-types/**/*.field-type.json` do not keep old per-item JSON forms; error codes use the app home root fixed collection file `error-codes.error.json`. | High (overrides JAR) |
| **Fixed Metadata Files** | `AppContext`, `RuntimeAppModuleDiffProvider`, `AppCustomStyleAppInfoContributor` | `app.metadata.json` (`AppContext.APP_INFO_FILE`) is map-merged across legacy-then-metadata sources, `modules.diff.json` (`RuntimeAppModuleDiffProvider.DIFF_FILE_NAME`) supplies module diff entries, and `style.css` (`AppCustomStyleAppInfoContributor.CUSTOM_STYLE_FILE_NAME`) supplies custom styles. | Merged by configured source priority |
| **Typed Models** | `SpringTypedModelMetaProvider` (`com.ouroboros.data.typed.runtime`) | Scans packages declared by `@DataModelScan` annotations and converts `@Model` classes into `DataModelMeta` (`TypedModelMetaHelper.scanModelMate`); registered as a `DataModelMetaProvider` with `@Priority(10)` | Medium |
| **Data Stations** | `SpringDataStationDefineProvider` (`com.ouroboros.data`) | Builds `DataStationDefine` entries from Spring `DataSource` beans and configuration | N/A (additive) |
| **VCS Workspace** | `LocalGitVCSManager` + `VCSFileInspector` SPI | Git repository files | Dev-time only |

### Provider SPI Contracts

**Core Providers**:
- `com.ouroboros.data.model.DataModelMetaProvider` - Dynamic data model metadata
- `com.ouroboros.businessmodel.BusinessModelMetaProvider` - Business model metadata
- `com.ouroboros.uimodel.UIModelProvider` - UI model definitions
- `com.ouroboros.uischema.UISchemaProvider` - UI schema definitions
- `com.ouroboros.data.station.DataStationDefineProvider` - Data station (datasource) definitions

**Dev-time Providers**:
- `com.ouroboros.vcs.file.inspector.VCSFileInspector` - VCS file classification and enrichment
- `com.ouroboros.devhelper.metadata.MetadataCollector` - Aggregated metadata collection for dev tools
- `com.ouroboros.dev.metadata.MetadataProvider` - Unified dev metadata access (`getAppAllMetadataByModule(appName, moduleName)`)

### Runtime Indexes (Centers)

**Model Centers** (SPI aggregation + indexing):
- `DataModelMetaCenter` - Aggregates all `DataModelMetaProvider` implementations
- `DataModelCenter` - Runtime index of `DataModel` instances (static lookup + `refresh()`)
- `BusinessModelMetaCenter` - Aggregates all `BusinessModelMetaProvider` implementations
- `BusinessModelCenter` - Runtime index of `BusinessModel` instances (static lookup + `refresh()`)
- `DataStationDefineCenter` - Aggregates all `DataStationDefineProvider` implementations
- `DataStationCenter` - Runtime index of data stations with `refresh()` hooks

**UI Indexes**:
- `UIRouteServiceImpl` - Immutable route list built from all `UIModelProvider` maps (`getAllRoute()`)
- `UISchemaController` - Runtime schema lookup via ordered `UISchemaProvider` SPI chain

**Dev Aggregation**:
- `HelperMetadataProvider` - Caches collected metadata per app/module (Redis cache name template `ouroboros-dev:%s:metadata`, snapshot key `snapshot`)
- `MetadataProviderMixer` - Priority-based conflict resolution across multiple providers

## Entry Points and Extension Points

### Extension Points

**1. Add New Model Source**

Implement a provider SPI and register it in `META-INF/services/`:

```java
// Example: Custom data model provider
public class CustomDataModelProvider implements DataModelMetaProvider {
    @Override
    public Optional<DataModelMeta> getDataModelMeta(String name) {
        return Optional.ofNullable(customModels.get(name));
    }

    @Override
    public Map<String, DataModelMeta> getDataModelMetaMap() {
        return customModels;
    }
}
```

Register in `META-INF/services/com.ouroboros.data.model.DataModelMetaProvider`:
```
com.example.CustomDataModelProvider
```

**2. Add VCS File Inspector**

Implement `VCSFileInspector` to classify and describe custom file types:

```java
public class CustomModelInspector implements VCSFileInspector {
    @Override
    public boolean support(VCSFile file) {
        return file != null && file.getPath().endsWith(".custom.json");
    }

    @Override
    public Optional<FileDescription> inspect(VCSFile file) {
        return Optional.of(new FileDescription()
            .setPath(file.getPath())
            .setName(file.getName())
            .setType("custom-model"));
    }

    @Override
    public Set<DirDescription> inspectDir(VCSFile file) {
        return Set.of();
    }
}
```

Register in `META-INF/services/com.ouroboros.vcs.file.inspector.VCSFileInspector`. There is no `@Priority` contract on the inspector itself; ordering/aggregation happens in `FileTreeBuilder` / `VCSManagerCenter`.

**3. Add Metadata Collector**

Implement `MetadataCollector` for dev-time aggregation:

```java
public class CustomMetadataCollector implements MetadataCollector {
    @Override
    public String getMetadataType() {
        return "custom";
    }

    @Override
    public List<Metadata> getAllMetadata() {
        return metadataList;
    }
}
```

Register in `META-INF/services/com.ouroboros.devhelper.metadata.MetadataCollector`.

### Entry Points

**Runtime Access**:
- `DataModelCenter.getDataModel(String name)` - Get data model by name (returns `Optional<DataModel>`)
- `BusinessModelCenter.getBusinessModel(String businessModelName)` - Get business model by name (returns `Optional<BusinessModel>`)
- `UISchemaController` - HTTP schema lookup, mapped at `/api/ui-schema` and `/api/amis-schema` (`GET /{*path}` and `GET ""`)
- `UIRouteServiceImpl.getAllRoute()` - Get all UI routes

**Dev-time Access**:
- `UIDevController` (`/api/dev/{appName}`) - Dev tool access via `MetadataProviderMixer` + direct provider fallback (`/ui`, `/all-channel/ui-model`, `/all-channel/ui-schema`)
- `RefreshMetadataCommand` - MQ command that triggers a context refresh and collects the app's metadata
- `DevMetadataRefreshController` - `POST /api/dev/{appName}/metadata/refresh` publishes `OuroborosContextRefreshEvent`
- `BaseDevelopService` - CRUD operations over VCS for model files

**Script Access**:
- `DataModelCenterScriptContextContributor` - Exposes `DataModelCenter` to script engines

## Lifecycle and State Machine

### Refresh Lifecycle

The metadata discovery pipeline follows an event-driven lifecycle orchestrated by `OuroborosContextRefreshLifecycle.publish(source, publisher)`, which publishes six ordered phases:

```
Platform Startup / Explicit Refresh Request
    ↓
OuroborosContextRefreshingEvent
    ↓
┌─────────────────────────────────────────────────────────┐
│ STAGE 1: Metadata Refreshing                            │
│                                                          │
│ Event: OuroborosMetadataRefreshingEvent                  │
│                                                          │
│ Representative listeners:                                │
│  - AppBusinessModelMetaProvider                          │
│  - LowcodeUIModelProvider                                │
│  - AppUiSchemaSchemaProvider                             │
│  - DefaultLogicFlowProvider                              │
│  - LowCodeMQBindingProvider, LowCodeScheduleTaskProvider │
│  - Statistics-reporting definition centers               │
│                                                          │
│ Actions:                                                │
│  - Reload filesystem models from app home               │
│  - Rebuild provider maps                                │
└─────────────────────────────────────────────────────────┘
    ↓
OuroborosMetadataRefreshedEvent
    ↓
┌─────────────────────────────────────────────────────────┐
│ STAGE 2: Model Refreshing                               │
│                                                          │
│ Events: OuroborosModelRefreshingEvent →                  │
│         OuroborosModelRefreshedEvent                     │
│                                                          │
│ Representative listeners:                                │
│  - BusinessModelExport (metadata-refreshed)              │
│  - RpcRuntimeAutoConfiguration (metadata-refreshed)      │
│  - ConfigurationRuntimeAutoConfiguration                 │
│  - DataDictRuntimeAutoConfiguration (model-refreshed)    │
│  - UiModelMicroWebAutoConfiguration (model-refreshed)    │
│                                                          │
│ Actions:                                                │
│  - DataStationCenter.refresh() / DataModelCenter.refresh()│
│  - BusinessModelCenter.refresh()                         │
│  - UI route index rebuild                                │
│                                                          │
│ Note: SpringDataStationAutoConfiguration is a plain      │
│ @Configuration that imports the station define provider; │
│ it is NOT an event listener.                             │
└─────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────┐
│ STAGE 3: Deferred Model Resolution                      │
│                                                          │
│ Listener chain:                                          │
│  - OuroborosTypedModelLifecycleAdapter                   │
│      @EventListener on OuroborosModelRefreshedEvent      │
│      → ModelRefreshedListener.onModelRefreshed()         │
│      → DeferredModelRequestRegistry.completePending(     │
│            DataModelCenter::getDataModel)                │
│                                                          │
│ Actions:                                                │
│  - Complete deferred typed model requests                │
│  - Resolve model proxies                                │
└─────────────────────────────────────────────────────────┘
    ↓
OuroborosContextRefreshedEvent
    ↓
Runtime Ready (Models Available)
```

### Trigger Points

**Automatic Triggers**:
1. Platform startup (`AppRuntimeAutoConfiguration` drives `OuroborosContextRefreshLifecycle`)

**Manual Triggers**:
1. HTTP API: `POST /api/dev/{appName}/metadata/refresh` (`DevMetadataRefreshController`, dev platform)
2. MQ Command: `RefreshMetadataCommand` (runtime, `Commands.REFRESH_METADATA`)
3. Programmatic: `ApplicationEventPublisher.publishEvent(new OuroborosContextRefreshEvent(...))`

## Sequence Diagram

### Metadata Discovery Flow

```
Developer/System          VCS/Filesystem       Provider SPI         Center/Index         Runtime/Dev UI
      |                        |                    |                    |                    |
      |--Trigger Refresh------>|                    |                    |                    |
      |                        |                    |                    |                    |
      |                   [OuroborosContextRefreshingEvent]                |                    |
      |                        |                    |                    |                    |
      |                   [OuroborosMetadataRefreshingEvent]               |                    |
      |                        |<--Reload Files-----|                    |                    |
      |                        |                    |                    |                    |
      |                        |---File Content---->|                    |                    |
      |                        |                    |                    |                    |
      |                        |              [Parse & Build Maps]       |                    |
      |                        |                    |                    |                    |
      |                   [OuroborosMetadataRefreshedEvent]                |                    |
      |                        |                    |                    |                    |
      |                        |                    |<--Aggregate SPI----|                    |
      |                        |                    |                    |                    |
      |                        |                    |---Provider Maps--->|                    |
      |                        |                    |                    |                    |
      |                        |                    |              [Build Indexes]            |
      |                        |                    |                    |                    |
      |                   [OuroborosModelRefreshingEvent]                  |                    |
      |                   [OuroborosModelRefreshedEvent]                   |                    |
      |                        |                    |                    |                    |
      |                        |                    |                    |<--Query Model------|
      |                        |                    |                    |                    |
      |                        |                    |                    |---Model Data------>|
      |                   [OuroborosContextRefreshedEvent]                 |                    |
```

### Dev-time Metadata Collection Flow

```
Dev Platform          MQ Command Bus       Runtime              MetadataCollector SPI    HelperMetadataProvider
      |                     |                  |                         |                         |
      |--RefreshMetadata--->|                  |                         |                         |
      |                     |                  |                         |                         |
      |                     |---Command------->|                         |                         |
      |                     |                  |                         |                         |
      |                     |            [RefreshMetadataCommand]         |                         |
      |                     |            [Publish context refresh]        |                         |
      |                     |                  |                         |                         |
      |                     |                  |<--Collect Metadata------|                         |
      |                     |                  |                         |                         |
      |                     |                  |---Metadata Records----->|                         |
      |                     |                  |                         |                         |
      |                     |                  |---Aggregated Metadata---------------------->|     |
      |                     |                  |                         |                         |
      |                     |                  |                         |      [Cache: ouroboros-dev:|
      |                     |                  |                         |       {app}:metadata]    |
      |                     |<--Response-------|                         |                         |
      |                     |                  |                         |                         |
      |<--Result------------|                  |                         |                         |
```

## Transport and Contracts

### Provider Contracts

**DataModelMetaProvider**:
```java
public interface DataModelMetaProvider {
    Optional<DataModelMeta> getDataModelMeta(String name);
    Map<String, DataModelMeta> getDataModelMetaMap();
}
```

**BusinessModelMetaProvider**:
```java
public interface BusinessModelMetaProvider {
    Optional<BusinessModelMeta> getBusinessModelMeta(String fullName);
    Map<String, BusinessModelMeta> getBusinessModelMetaMap();
}
```

**UIModelProvider**:
```java
public interface UIModelProvider {
    Optional<UIModel> getUIModel(String path);
    Map<String, UIModel> getUIModelMap();
}
```

**UISchemaProvider**:
```java
public interface UISchemaProvider {
    Optional<Object> getUISchema(String path);
    default Optional<Object> getUISchema(String path, Map<String, Object> context) {
        return getUISchema(path);
    }
    Map<String, Object> getUISchemaMap();
}
```

**DataStationDefineProvider**:
```java
public interface DataStationDefineProvider {
    Optional<DataStationDefine> getDataStationDefine(String name);
    Map<String, DataStationDefine> getDataStationDefineMap();
}
```

**MetadataCollector**:
```java
public interface MetadataCollector {
    String getMetadataType();
    List<Metadata> getAllMetadata();
}
```

### Event Contracts

All lifecycle events extend `OuroborosEvent`, which is backed by `java.util.EventObject` (not Spring's `ApplicationEvent`):

```java
public abstract class OuroborosEvent extends EventObject {
    public OuroborosEvent(Object source) {
        super(source);
    }
}
```

**OuroborosMetadataRefreshingEvent**:
```java
public class OuroborosMetadataRefreshingEvent extends OuroborosEvent {
    public OuroborosMetadataRefreshingEvent(Object source) {
        super(source);
    }
}
```

**OuroborosModelRefreshedEvent**:
```java
public class OuroborosModelRefreshedEvent extends OuroborosEvent {
    public OuroborosModelRefreshedEvent(Object source) {
        super(source);
    }
}
```

The full event set published by `OuroborosContextRefreshLifecycle` is: `OuroborosContextRefreshingEvent`, `OuroborosMetadataRefreshingEvent`, `OuroborosMetadataRefreshedEvent`, `OuroborosModelRefreshingEvent`, `OuroborosModelRefreshedEvent`, `OuroborosContextRefreshedEvent` (source: `com.ouroboros.lifecycle.event`). `OuroborosContextRefreshEvent` is the event used to *request* a refresh.

### VCS Integration Contracts

**VCSFileInspector**:
```java
public interface VCSFileInspector {
    default boolean support(VCSFile file) {
        return false;
    }
    Optional<FileDescription> inspect(VCSFile file);
    Set<DirDescription> inspectDir(VCSFile file);
}
```

Registration is via `META-INF/services/com.ouroboros.vcs.file.inspector.VCSFileInspector`; roughly twenty modules register an inspector. `AbstractVCSFileInspector` provides a convenience base class, and `DefaultVCSFileInspector` is the built-in fallback.

**FileDescription / DirDescription** (enriched tree nodes; both implement `DescriptionNode`):

```java
public interface DescriptionNode {
    String getParent();
    String getPath();
    String getName();
    String getTitle();
    String getIcon();
    String getDescription();
    List<? extends DescriptionNode> getChildren();
}

public class FileDescription implements DescriptionNode {
    private String parent;
    private String path;
    private String fileName;      // actual file name on disk
    private String name;
    private String title;
    private String description;
    private String icon;
    private String type;
    private String size;
    private String lastModified;
    private String lastModifiedBy;
    private String created;
    private String createdBy;
    private String version;
    private String versionState;
    private String lockBy;
    private String lockState;
}

public class DirDescription implements DescriptionNode {
    private String parent;
    private String path;
    private String name;
    private String title;
    private String icon;
    private String description;
    private List<DescriptionNode> children;
}
```

There is no `FileNode` class and no `FileType` enum in the implemented contract; file vs. directory is expressed by the concrete type.

## Failure Modes and Recovery

### Provider and lifecycle failures

Failure behavior belongs to the concrete source and aggregator. `DataModelMetaCenter` consumes `DataServices` streams directly; `DataServices` uses `ServiceLoader` and does not install a general catch-and-skip policy. A provider construction or lookup failure can therefore propagate. File providers may handle an individual parse failure locally; inspect that provider's logs and return path before treating a missing model as a deliberate skip.

`OuroborosContextRefreshLifecycle.publishPhase` publishes a fallback `OuroborosBootstrapFailedEvent` for a runtime exception and rethrows it. Later phases do not complete normally after that exception. `publish` holds the lifecycle publication write lock; synchronous business executions using `withStableContext` hold its read lock. After a refresh failure, that business entry fails closed until a complete successful refresh. This guards participating callers, not every direct center lookup. Refresh is not a global atomic snapshot swap: some listeners may already have changed state. The pipeline does not guarantee that every previous model remains available, a timeout aborts all work, or every loader runs JSON Schema validation.

For recovery, retain the first exception, source/model name and failed phase, repair the actual provider or file, then trigger the existing refresh entry and verify the required centers/routes. Clear only the affected dev cache when cache evidence justifies it. A completed event sequence still needs the caller's model and route checks.

## Security and Tenancy

Application workspaces, per-app VCS managers and application-prefixed metadata cache keys scope development metadata. Application scope is not a tenant security boundary.

Lifecycle events do not carry a tenant field. Unified tenant context propagation, provider filtering, tenant-partitioned indexes and API isolation are not implemented; see [current tenancy boundaries](../../cross-cutting/multi-tenancy.md). Do not infer tenant isolation from app-scoped discovery.

Authentication, API authorization and data permissions remain owned by the [security design](../../../../security/DESIGN.md) and concrete provider/consumer entry points. Metadata refresh does not add independent authorization or sensitive-field masking.

## Code Map

### Core Provider Implementations

**Data Models**:
- `core/ouroboros-data/ouroboros-data-typed-runtime/src/main/java/com/ouroboros/data/typed/runtime/SpringTypedModelMetaProvider.java`
- `core/ouroboros-data/ouroboros-data-runtime/src/main/java/com/ouroboros/data/SpringDataStationDefineProvider.java`

**Business Models**:
- `core/ouroboros-business-model/ouroboros-business-model-runtime/src/main/java/com/ouroboros/businessmodel/AppBusinessModelMetaProvider.java`

**UI Models**:
- `core/ouroboros-ui-model/ouroboros-ui-model-runtime/src/main/java/com/ouroboros/uimodel/JarUIModelProvider.java`
- `core/ouroboros-ui-model/ouroboros-ui-model-lowcode/src/main/java/com/ouroboros/uimodel/lowcode/LowcodeUIModelProvider.java`

**UI Schemas**:
- `core/ouroboros-ui-model/ouroboros-ui-model-schema/src/main/java/com/ouroboros/uischema/JarUISchemaProvider.java`
- `core/ouroboros-ui-model/ouroboros-ui-model-schema-runtime/src/main/java/com/ouroboros/uischema/AppUiSchemaSchemaProvider.java`

### Runtime Indexes

**Centers**:
- `core/ouroboros-data/ouroboros-data-core/src/main/java/com/ouroboros/data/model/DataModelMetaCenter.java`
- `core/ouroboros-data/ouroboros-data-core/src/main/java/com/ouroboros/data/model/DataModelCenter.java`
- `core/ouroboros-data/ouroboros-data-core/src/main/java/com/ouroboros/data/station/DataStationDefineCenter.java`
- `core/ouroboros-data/ouroboros-data-core/src/main/java/com/ouroboros/data/station/DataStationCenter.java`
- `core/ouroboros-business-model/ouroboros-business-model-core/src/main/java/com/ouroboros/businessmodel/BusinessModelMetaCenter.java`
- `core/ouroboros-business-model/ouroboros-business-model-core/src/main/java/com/ouroboros/businessmodel/BusinessModelCenter.java`

**UI Access**:
- `core/ouroboros-ui-model/ouroboros-ui-model-runtime/src/main/java/com/ouroboros/uimodel/service/impl/UIRouteServiceImpl.java`
- `core/ouroboros-ui-model/ouroboros-ui-model-schema-runtime/src/main/java/com/ouroboros/uischema/controller/UISchemaController.java`

### VCS Integration

**Core VCS**:
- `dev-module/ouroboros-dev-version-control-system/src/main/java/com/ouroboros/vcs/VCSManagerCenter.java`
- `dev-module/ouroboros-dev-version-control-system/src/main/java/com/ouroboros/vcs/LocalGitVCSManager.java`
- `dev-module/ouroboros-dev-version-control-system/src/main/java/com/ouroboros/vcs/file/inspector/FileTreeBuilder.java`
- `dev-module/ouroboros-dev-version-control-system/src/main/java/com/ouroboros/vcs/file/inspector/VCSFileInspector.java`
- `dev-module/ouroboros-dev-version-control-system/src/main/java/com/ouroboros/vcs/file/inspector/AbstractVCSFileInspector.java`
- `dev-module/ouroboros-dev-version-control-system/src/main/java/com/ouroboros/vcs/file/inspector/FileDescription.java`
- `dev-module/ouroboros-dev-version-control-system/src/main/java/com/ouroboros/vcs/file/inspector/DirDescription.java`

**Inspectors** (registered in `META-INF/services/com.ouroboros.vcs.file.inspector.VCSFileInspector`):
- `core/ouroboros-business-model/ouroboros-business-model-dev/` - Business model inspectors
- `core/ouroboros-ui-model/ouroboros-ui-model-dev/` - UI model inspector
- `core/ouroboros-ui-model/ouroboros-ui-model-schema-dev/` - UI schema inspector
- `core/ouroboros-ui-model/ouroboros-ui-model-microweb-dev/` - Micro-web inspector
- (plus `ouroboros-logicflow-dev`, `ouroboros-field-type-dev`, `ouroboros-error-code-dev`, `ouroboros-i18n-dev`, `ouroboros-resource-dev`, `ouroboros-webapi-model-dev`, `ouroboros-scheduler-dev`, `ouroboros-notification-dev`, `ouroboros-rpc-model-dev`, `ouroboros-data-dict-dev`, `ouroboros-configuration-dev`, `ouroboros-business-log-dev`, `ouroboros-message-queue-dev`, `ouroboros-app-module-model-dev`, `ouroboros-statistics-reporting-dev`, and `ouroboros-dev-app-management`)

### Dev Aggregation

**Metadata Collection**:
- `dev-module/ouroboros-dev-helper/ouroboros-dev-helper-runtime/src/main/java/com/ouroboros/devhelper/command/RefreshMetadataCommand.java`
- `dev-module/ouroboros-dev-helper/ouroboros-dev-helper-runtime/src/main/java/com/ouroboros/devhelper/metadata/collector/DataModelMetadataCollector.java`
- `dev-module/ouroboros-dev-helper/ouroboros-dev-helper-runtime/src/main/java/com/ouroboros/devhelper/metadata/collector/BusinessModelMetadataCollector.java`
- `dev-module/ouroboros-dev-helper/ouroboros-dev-helper-dev-metadata-provider/src/main/java/com/ouroboros/devhelper/dev/metadata/provider/HelperMetadataProvider.java`

**Dev Access**:
- `libs/common/common-dev/src/main/java/com/ouroboros/dev/metadata/MetadataProviderMixer.java`
- `libs/common/common-dev/src/main/java/com/ouroboros/dev/metadata/MetadataProvider.java`
- `core/ouroboros-ui-model/ouroboros-ui-model-dev/src/main/java/com/ouroboros/uimodel/dev/controller/UIDevController.java`
- `dev-module/ouroboros-dev-app-management/src/main/java/com/ouroboros/dev/app/controller/DevMetadataRefreshController.java`

### Lifecycle Orchestration

- `libs/common/common-app/src/main/java/com/ouroboros/lifecycle/OuroborosContextRefreshLifecycle.java`
- `libs/common/common-app/src/main/java/com/ouroboros/lifecycle/event/OuroborosEvent.java`
- `ability/ouroboros-app-runtime/src/main/java/com/ouroboros/app/AppRuntimeAutoConfiguration.java`
- `core/ouroboros-data/ouroboros-data-runtime/src/main/java/com/ouroboros/data/SpringDataStationAutoConfiguration.java`
- `core/ouroboros-data/ouroboros-data-platform-adapter/src/main/java/com/ouroboros/data/runtime/adapter/OuroborosTypedModelLifecycleAdapter.java`
- `core/ouroboros-data/ouroboros-data-typed-runtime/src/main/java/com/ouroboros/data/typed/runtime/ModelRefreshedListener.java`

## Current Limitations

1. **No incremental refresh**: Full reload on every refresh, even for single file changes
2. **No metadata versioning**: No migration support when model schemas evolve
3. **Limited remote sources**: Only local filesystem and classpath, no HTTP/S3/database
4. **No change detection**: Cannot detect which models changed since last refresh
5. **Synchronous refresh**: Blocks until all providers complete, no async/streaming

**Related Documents:**
- [Remote Debugging Pipeline](remote-debugging.md)
- [Online Debugging Data Exchange Pipeline](online-debugging-exchange.md)
- [Development Module Pattern Guide](../../../guides/development-module-guide.md)
