package com.ouroboros.workflow.approval.external;

/**
 * 外部待办中心镜像任务查询。
 *
 * @param userId       用户 ID（可空）
 * @param connectionId 连接 ID
 * @param cursor       游标（可空）
 * @param limit        页大小
 */
public record ExternalTaskQuery(String userId, String connectionId, String cursor, int limit) {
}
