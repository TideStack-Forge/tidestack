package com.ouroboros.workflow.approval.external;

import java.util.List;

/**
 * 外部实例分页（增量对账）。
 *
 * @param instances 实例快照列表
 * @param cursor    下一页游标（可空；为空表示没有更多）
 */
public record ExternalInstancePage(List<ExternalInstanceSnapshot> instances, String cursor) {
}
