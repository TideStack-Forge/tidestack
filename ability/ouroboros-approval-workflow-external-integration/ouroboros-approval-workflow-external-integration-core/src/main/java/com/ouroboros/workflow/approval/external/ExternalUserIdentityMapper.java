package com.ouroboros.workflow.approval.external;

import java.util.Optional;

/**
 * 平台用户与外部引擎用户标识的显式映射 seam。
 *
 * <p><b>契约</b>：{@link ExternalStartCommand}、{@link ExternalActionCommand} 等规范命令中的用户标识
 * 一律是<b>平台规范用户 ID</b>（{@code userIdentity} 语义）；外部引擎的用户标识（钉钉 {@code userId}/
 * {@code unionId}、企微 {@code userid}、BPM 本地账号等）由<b>适配器在 ACL 内翻译</b>，禁止把平台 ID
 * 原样透传给外部 API，也禁止把外部字符串直接当平台用户。
 *
 * <p>适配器在实现 {@code start}/{@code execute} 时应使用本接口完成翻译：出站方向
 * {@link #toExternalUser} 在调用外部 API 前把平台用户解析为外部标识；入站方向
 * {@link #toPlatformUser} 在回调/查询结果归一化时把外部标识解析回平台用户。找不到映射时必须返回
 * {@link Optional#empty()} 并进入待处理/异常状态（错误码 {@code EXTERNAL_USER_MAPPING_MISSING}），
 * 不允许猜测或直接使用原始字符串。
 *
 * <p>实现可按 Java SPI（{@code META-INF/services/com.ouroboros.workflow.approval.external.ExternalUserIdentityMapper}）
 * 或 Spring Bean 注册（现役钉钉实现为 Bean 注册，见决策 D-7），并可按 {@code adapterId + connectionId}
 * 作用域隔离（例如钉钉按 {@code corpId} 缓存手机号/工号到 {@code userId} 的映射，手机号只用于发现、
 * 不能当稳定主键）。
 */
public interface ExternalUserIdentityMapper {

  /**
   * 出站：把平台用户解析为外部引擎用户标识。
   *
   * @param platformUserId 平台规范用户 ID
   * @param adapterId      适配器 ID
   * @param connectionId   连接 ID（外部标识的作用域）
   * @return 外部用户标识；无法解析时为空
   */
  Optional<String> toExternalUser(String platformUserId, String adapterId, String connectionId);

  /**
   * 入站：把外部引擎用户标识解析回平台用户。
   *
   * @param externalUserId 外部用户标识
   * @param adapterId      适配器 ID
   * @param connectionId   连接 ID
   * @return 平台规范用户 ID；无映射时为空
   */
  Optional<String> toPlatformUser(String externalUserId, String adapterId, String connectionId);
}
