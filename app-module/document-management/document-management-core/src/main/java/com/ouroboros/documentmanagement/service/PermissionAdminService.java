package com.ouroboros.documentmanagement.service;

import java.util.List;
import java.util.Optional;

import com.ouroboros.documentmanagement.model.acl.Permission;
import com.ouroboros.documentmanagement.model.acl.AclEntry;
import com.ouroboros.documentmanagement.model.acl.PermissionSet;
import com.ouroboros.documentmanagement.model.acl.ResourceType;

/**
 * 权限服务接口，通过 AclEntryRepository 管理文档库和文档库内容的权限
 */
public interface PermissionAdminService {
    /**
     * 为资源添加权限条目
     *
     * @param libraryId      文档库ID
     * @param resourceId     资源ID
     * @param resourceType   资源类型
     * @param principalId    主体ID
     * @param principalType  主体类型
     * @param permissions    权限集合
     * @return 权限条目
     */
    AclEntry setPermission(String libraryId, String resourceId, ResourceType resourceType,
                           String principalId, String principalType,
                           PermissionSet permissions);

    /**
     * 移除权限条目
     *
     * @param permissionEntryId 权限条目ID
     */
    void removePermission(String permissionEntryId);

    /**
     * 移除权限条目
     *
     * @param resourceId     资源ID
     * @param resourceType   资源类型
     * @param principalId    主体ID
     * @param principalType  主体类型
     */
    void removePermission(String resourceId, ResourceType resourceType, String principalId, String principalType);

    /**
     * 更新权限条目
     *
     * @param permissionEntryId 权限条目ID
     * @param permissions       新的权限集合
     * @return 更新后的权限条目
     */
    AclEntry updatePermission(String permissionEntryId, PermissionSet permissions);

    /**
     * 获取权限条目
     *
     * @param permissionEntryId 权限条目ID
     * @return 权限条目
     */
    Optional<AclEntry> getPermission(String permissionEntryId);

    /**
     * 获取权限条目
     *
     * @param resourceId     源资源ID
     * @param resourceType   资源类型
     * @param principalId    主体ID
     * @param principalType  主体类型
     * @return 权限条目
     */
    Optional<AclEntry> getPermission(String resourceId, ResourceType resourceType, String principalId, String principalType);

    /**
     * 获取资源的所有权限条目
     *
     * @param resourceId   资源ID
     * @param resourceType 资源类型
     * @return 权限条目列表
     */
    List<AclEntry> getPermissions(String resourceId, ResourceType resourceType);
}
