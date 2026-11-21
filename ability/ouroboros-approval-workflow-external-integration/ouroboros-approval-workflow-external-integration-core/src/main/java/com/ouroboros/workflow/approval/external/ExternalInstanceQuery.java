package com.ouroboros.workflow.approval.external;

/**
 * 外部实例增量对账查询。禁止基于 offset 拼接分页。
 *
 * @param connectionId  连接 ID（适配器按连接解析外部状态所需）
 * @param changedFrom   固定窗口开始（含，可空）
 * @param changedUntil  固定窗口结束（不含，可空）
 * @param status        状态过滤（可空）
 * @param cursor        游标（适配器定义语义，可空）
 * @param limit         页大小
 */
public record ExternalInstanceQuery(
    String connectionId,
    String changedFrom,
    String changedUntil,
    String status,
    String cursor,
    int limit) {
}
