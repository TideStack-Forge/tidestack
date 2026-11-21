package com.ouroboros.workflow.approval.external;

import java.util.List;
import java.util.Map;

/**
 * 外部定义表单 schema。字段细节由适配器定义；启动前校验字段映射使用。
 *
 * @param components 组件清单（适配器定义语义，可空）
 * @param raw        原始 schema
 */
public record ExternalFormSchema(List<Map<String, Object>> components, Map<String, Object> raw) {
}
