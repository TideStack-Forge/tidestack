package com.ouroboros.documentmanagement.service.impl;

import com.ouroboros.documentmanagement.model.DocumentLibrary;
import com.ouroboros.documentmanagement.model.LibraryNode;
import com.ouroboros.documentmanagement.model.acl.AclEntry;
import com.ouroboros.documentmanagement.model.acl.Permission;
import com.ouroboros.documentmanagement.model.acl.PermissionSet;
import com.ouroboros.documentmanagement.model.acl.PrincipalResolver;
import com.ouroboros.documentmanagement.model.acl.PrincipalType;
import com.ouroboros.documentmanagement.model.acl.PrincipalTypeProvider;
import com.ouroboros.documentmanagement.model.acl.ResourceType;
import com.ouroboros.documentmanagement.repository.AclEntryRepository;
import com.ouroboros.documentmanagement.repository.LibraryNodeRepository;
import com.ouroboros.documentmanagement.repository.LibraryRepository;
import com.ouroboros.documentmanagement.service.ACLService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * 权限控制服务实现类
 *
 * <p>负责计算用户的有效权限，通过遍历资源路径（从文档库到节点）累积权限。
 *
 * <p>设计要点：
 * <ul>
 *   <li>权限继承：子节点继承父节点的权限</li>
 *   <li>权限累积：多个权限条目的权限会合并（并集）</li>
 *   <li>主体解析：通过 PrincipalTypeProvider 获取主体解析器</li>
 * </ul>
 */
@Service("aclService")
public class ACLServiceImpl implements ACLService {

  private final LibraryRepository libraryRepository;
  private final LibraryNodeRepository nodeRepository;
  private final AclEntryRepository aclEntryRepository;

  public ACLServiceImpl(
      LibraryRepository libraryRepository,
      LibraryNodeRepository nodeRepository,
      AclEntryRepository aclEntryRepository) {
    this.libraryRepository = libraryRepository;
    this.nodeRepository = nodeRepository;
    this.aclEntryRepository = aclEntryRepository;
  }

  @Override
  public PermissionSet getEffectivePermissions(
      String resourceId, ResourceType resourceType, String userId) {
    // 1. 获取资源路径（从文档库到当前资源）
    List<Object> resourcePath = getResourcePath(resourceId, resourceType);

    // 2. 检查用户是否是文档库的所有者或管理员
    //    如果是，则拥有所有权限
    if (!resourcePath.isEmpty() && resourcePath.get(0) instanceof DocumentLibrary) {
      DocumentLibrary library = (DocumentLibrary) resourcePath.get(0);
      if (isOwnerOrAdmin(library, userId)) {
        return PermissionSet.all();
      }
    }

    // 3. 累积路径上所有相关的权限
    PermissionSet effectivePermissions = PermissionSet.empty();

    for (Object resource : resourcePath) {
      // 获取该资源的所有权限条目
      List<AclEntry> entries = getResourceAclEntries(resource);

      // 过滤出与当前用户相关的权限条目
      for (AclEntry entry : entries) {
        if (isEntryRelevantToUser(entry, userId)) {
          // 合并权限（并集）
          effectivePermissions = effectivePermissions.merge(entry.getPermissions());
        }
      }
    }

    return effectivePermissions;
  }

  /**
   * 判断用户是否是文档库的所有者或管理员
   *
   * @param library 文档库
   * @param userId  用户ID
   * @return 如果是所有者或管理员返回 true
   */
  private boolean isOwnerOrAdmin(DocumentLibrary library, String userId) {
    // 检查是否是所有者
    if (userId.equals(library.getOwner())) {
      return true;
    }

    // 检查是否是管理员
    List<String> adminIds = library.getAdminIds();
    if (adminIds != null && adminIds.contains(userId)) {
      return true;
    }

    return false;
  }

  @Override
  public boolean hasPermission(
      String resourceId, ResourceType resourceType, String userId, Permission permission) {
    PermissionSet effectivePermissions = getEffectivePermissions(resourceId, resourceType, userId);
    return effectivePermissions.has(permission);
  }

  @Override
  public boolean hasAnyPermission(
      String resourceId, ResourceType resourceType, String userId, Permission... permissions) {
    PermissionSet effectivePermissions = getEffectivePermissions(resourceId, resourceType, userId);
    return effectivePermissions.hasAny(permissions);
  }

  @Override
  public boolean hasAllPermissions(
      String resourceId, ResourceType resourceType, String userId, Permission... permissions) {
    PermissionSet effectivePermissions = getEffectivePermissions(resourceId, resourceType, userId);
    return effectivePermissions.hasAll(permissions);
  }

  /**
   * 获取资源路径（从根目录到当前资源）
   *
   * @param resourceId   资源ID
   * @param resourceType 资源类型
   * @return 资源路径列表（按照从根目录到当前资源的顺序）
   */
  private List<Object> getResourcePath(String resourceId, ResourceType resourceType) {
    List<Object> path = new ArrayList<>();

    if (resourceType == ResourceType.LIBRARY) {
      // 文档库：查询其根目录的权限
      Optional<DocumentLibrary> libraryOpt = libraryRepository.findById(resourceId);
      if (libraryOpt.isPresent()) {
        DocumentLibrary library = libraryOpt.get();
        // 添加文档库（用于所有者/管理员检查）
        path.add(library);
        // 添加根目录（用于权限继承）
        nodeRepository.findRootDirectory(resourceId).ifPresent(path::add);
      }
    } else if (resourceType == ResourceType.DIRECTORY || resourceType == ResourceType.DOCUMENT) {
      // 目录或文档：需要构建完整路径
      Optional<LibraryNode> nodeOpt = nodeRepository.findById(resourceId);
      if (nodeOpt.isPresent()) {
        LibraryNode node = nodeOpt.get();
        // 获取节点路径（从当前节点到根节点）
        List<LibraryNode> nodePath = nodeRepository.findPathToNode(resourceId);

        // 添加文档库
        Optional<DocumentLibrary> libraryOpt = libraryRepository.findById(node.getLibraryId());
        libraryOpt.ifPresent(lib -> path.add(0, lib));

        // 添加节点路径（已经是从根到当前节点的顺序）
        path.addAll(nodePath);
      }
    }

    return path;
  }

  /**
   * 获取资源的所有权限条目
   *
   * @param resource 资源对象（DocumentLibrary 或 LibraryNode）
   * @return 权限条目列表
   */
  private List<AclEntry> getResourceAclEntries(Object resource) {
    if (resource instanceof DocumentLibrary) {
      // 文档库本身不再存储权限条目，权限存储在根目录上
      return Collections.emptyList();
    } else if (resource instanceof LibraryNode) {
      LibraryNode node = (LibraryNode) resource;
      ResourceType resourceType =
          switch (node.getType()) {
            case DIRECTORY -> ResourceType.DIRECTORY;
            case DOCUMENT -> ResourceType.DOCUMENT;
          };
      return aclEntryRepository.findByResource(node.getId(), resourceType);
    }
    return Collections.emptyList();
  }

  /**
   * 判断权限条目是否与指定用户相关
   *
   * @param entry  权限条目
   * @param userId 用户ID
   * @return 如果相关返回 true
   */
  private boolean isEntryRelevantToUser(AclEntry entry, String userId) {
    // 1. 获取主体类型
    Optional<PrincipalType> principalTypeOpt =
        PrincipalTypeProvider.get(entry.getPrincipalType());

    if (!principalTypeOpt.isPresent()) {
      return false;
    }

    // 2. 使用主体解析器判断用户是否属于该主体
    PrincipalResolver resolver = principalTypeOpt.get().getResolver();
    return resolver.containsUser(entry.getPrincipal(), userId);
  }

  @Override
  public List<AclEntry> getUserRelatedAclEntries(String userId) {
    // 使用 LinkedHashSet 保持顺序并去重
    Set<AclEntry> relatedEntries = new LinkedHashSet<>();

    // 1. 获取所有的 PrincipalType
    List<PrincipalType> allPrincipalTypes = PrincipalTypeProvider.getAll();

    // 2. 遍历每个 PrincipalType
    for (PrincipalType principalType : allPrincipalTypes) {
      // 3. 获取该用户在该 PrincipalType 下的所有 principal
      PrincipalResolver resolver = principalType.getResolver();
      List<String> principals = resolver.getPrincipalsForUser(userId);

      // 4. 对每个 principal 查询其权限条目
      for (String principal : principals) {
        List<AclEntry> entries = aclEntryRepository.findByPrincipal(principal, principalType.getName());
        relatedEntries.addAll(entries);
      }
    }

    // 5. 转换为 List 返回
    return new ArrayList<>(relatedEntries);
  }
}
