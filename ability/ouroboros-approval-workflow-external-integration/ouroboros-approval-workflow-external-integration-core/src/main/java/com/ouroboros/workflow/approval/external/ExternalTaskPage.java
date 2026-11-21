package com.ouroboros.workflow.approval.external;

import java.util.List;

/**
 * 外部任务分页（Phase 4 M26 稳定游标）。
 *
 * @param tasks      任务快照列表
 * @param hasMore    是否还有下一页（无稳定游标时按 updatedAt 上限截断并置 true）
 * @param nextCursor 下一页游标（可空；非空表示调用方可携带该游标续拉，聚合器按此修复多源缺页）
 */
public record ExternalTaskPage(List<ExternalTaskSnapshot> tasks, boolean hasMore, String nextCursor) {
}
