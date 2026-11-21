package com.ouroboros.workflow.approval.external;

import java.util.List;
import java.util.Map;

/**
 * 外部任务表单快照。
 *
 * @param fields 表单字段（键为字段标识，值为字段值）
 */
public record ExternalFormSnapshot(Map<String, Object> fields) {
}
