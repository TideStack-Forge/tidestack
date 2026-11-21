package com.ouroboros.workflow.approval.external;

import java.util.Set;

/**
 * 适配器静态描述。只通过 provider 暴露，不从实现类名推断。
 *
 * <p>描述里只保留"有决策消费者"的字段：
 * <ul>
 *   <li>{@code canonicalActions} 与 {@code disabledActions} 由映射激活校验
 *       （{@code WorkflowEngineMappingValidator}）使用；</li>
 *   <li>{@code extraCapabilities} 参与能力派生（{@code EngineAdapterCapabilities.derive}）；</li>
 *   <li>接入模式（RESULT_ONLY/FULL_PROCESS）与交互模式（PROXY/LINK）由 Registry 按端口实现派生，
 *       连接类型档案与应用类型已从连接字段契约移除，因此都不再在描述里重复声明。</li>
 * </ul>
 *
 * @param adapterId         稳定且全局唯一的适配器 ID（小写字母/数字/连字符）
 * @param displayName       显示名称
 * @param version           适配器版本
 * @param canonicalActions  该适配器实际支持的规范动作集（approve/reject/transfer/addSign/remind 等）
 * @param extraCapabilities 超出端口表达能力的细粒度能力声明（默认空集；端口级能力由 Registry 派生）
 * @param disabledActions   全局禁用的规范动作（官方不支持，激活时拒绝），如 "rollback"/"addSign"/"freeApprove"
 */
public record ApprovalWorkflowEngineAdapterDescriptor(
    String adapterId,
    String displayName,
    String version,
    Set<String> canonicalActions,
    Set<Capability> extraCapabilities,
    Set<String> disabledActions) {

}
