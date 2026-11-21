package com.ouroboros.organizationstructure.entity;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.AuditFields;

import java.time.LocalDateTime;

@Model(rawName = "t_sys_admin_level", label = "行政级别")
@AuditFields
public class AdminLevelEntity {

    @PrimaryKey(generator = "snowflake")
    @Field
    private String id;

    @Field
    private String code;

    @Field
    private String name;

    @Field(type = "Integer")
    private Integer rank;

    @Field
    private String scope;

    @Field(nullable = true)
    private String description;

    @Field(type = "Boolean")
    private Boolean enabled;

    @Field(nullable = true)
    private String createdBy;

    @Field(nullable = true)
    private LocalDateTime createdAt;

    @Field(nullable = true)
    private String updatedBy;

    @Field(nullable = true)
    private LocalDateTime updatedAt;

    public AdminLevelEntity() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
