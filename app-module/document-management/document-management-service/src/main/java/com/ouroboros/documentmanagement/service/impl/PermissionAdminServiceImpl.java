package com.ouroboros.documentmanagement.service.impl;

import com.ouroboros.documentmanagement.exception.ResourceNotFoundException;
import com.ouroboros.documentmanagement.model.acl.AclEntry;
import com.ouroboros.documentmanagement.model.acl.PermissionSet;
import com.ouroboros.documentmanagement.model.acl.ResourceType;
import com.ouroboros.documentmanagement.repository.AclEntryRepository;
import com.ouroboros.documentmanagement.service.PermissionAdminService;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * 权限管理服务实现类
 *
 * <p>负责权限条目的 CRUD 操作，提供权限管理功能。
 *
 * <p>设计要点：
 * <ul>
 *   <li>权限条目的增删改查</li>
 *   <li>支持按资源、主体、组合条件查询</li>
 *   <li>不进行权限验证（由调用方验证）</li>
 * </ul>
 */
@Service("permissionAdminService")
public class PermissionAdminServiceImpl implements PermissionAdminService {

  private final AclEntryRepository aclEntryRepository;

  public PermissionAdminServiceImpl(AclEntryRepository aclEntryRepository) {
    this.aclEntryRepository = aclEntryRepository;
  }

  @Override
  public AclEntry setPermission(
      String libraryId,
      String resourceId,
      ResourceType resourceType,
      String principalId,
      String principalType,
      PermissionSet permissions) {
    // 1. 查找是否已存在相同的权限条目
    Optional<AclEntry> existingEntry =
        aclEntryRepository.findByResourceAndPrincipal(
            resourceId, resourceType, principalId, principalType);

    if (existingEntry.isPresent()) {
      // 2a. 如果存在，更新权限
      AclEntry entry = existingEntry.get();
      // 注意：这里需要创建一个新的 AclEntry 对象，因为模型可能是不可变的
      // 实际实现中应该调用 entry 的 builder 或者工厂方法
      return aclEntryRepository.update(createUpdatedEntry(entry, permissions));
    } else {
      // 2b. 如果不存在，创建新的权限条目
      AclEntry newEntry = createNewEntry(libraryId, resourceId, resourceType, principalId, principalType, permissions);
      return aclEntryRepository.add(newEntry);
    }
  }

  @Override
  public void removePermission(String permissionEntryId) {
    aclEntryRepository.deleteById(permissionEntryId);
  }

  @Override
  public void removePermission(
      String resourceId, ResourceType resourceType, String principalId, String principalType) {
    aclEntryRepository.deleteByResourceAndPrincipal(resourceId, resourceType, principalId, principalType);
  }

  @Override
  public AclEntry updatePermission(String permissionEntryId, PermissionSet permissions) {
    // 1. 查找权限条目
    Optional<AclEntry> existingEntry = aclEntryRepository.findById(permissionEntryId);

    if (!existingEntry.isPresent()) {
      throw new ResourceNotFoundException("权限条目", permissionEntryId);
    }

    // 2. 更新权限
    AclEntry entry = existingEntry.get();
    AclEntry updatedEntry = createUpdatedEntry(entry, permissions);
    return aclEntryRepository.update(updatedEntry);
  }

  @Override
  public Optional<AclEntry> getPermission(String permissionEntryId) {
    return aclEntryRepository.findById(permissionEntryId);
  }

  @Override
  public Optional<AclEntry> getPermission(
      String resourceId, ResourceType resourceType, String principalId, String principalType) {
    return aclEntryRepository.findByResourceAndPrincipal(
        resourceId, resourceType, principalId, principalType);
  }

  @Override
  public List<AclEntry> getPermissions(String resourceId, ResourceType resourceType) {
    return aclEntryRepository.findByResource(resourceId, resourceType);
  }

  /**
   * 创建新的权限条目
   *
   * @param libraryId     文档库ID
   * @param resourceId    资源ID
   * @param resourceType  资源类型
   * @param principalId   主体ID
   * @param principalType 主体类型
   * @param permissions   权限集合
   * @return 新的权限条目
   */
  private AclEntry createNewEntry(
      String libraryId,
      String resourceId,
      ResourceType resourceType,
      String principalId,
      String principalType,
      PermissionSet permissions) {
    return AclEntry.builder()
        .libraryId(libraryId)
        .resourceId(resourceId)
        .resourceType(resourceType)
        .principal(principalId)
        .principalType(principalType)
        .permissions(permissions)
        .build();
  }

  /**
   * 创建更新后的权限条目
   *
   * <p>使用 toBuilder 创建副本并更新权限字段。
   *
   * @param entry       原权限条目
   * @param permissions 新的权限集合
   * @return 更新后的权限条目
   */
  private AclEntry createUpdatedEntry(AclEntry entry, PermissionSet permissions) {
    return entry.toBuilder()
        .permissions(permissions)
        .build();
  }
}
