package com.ouroboros.businessmodel.action;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.data.dsl.Operators;
import com.ouroboros.data.dsl.SExpression;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.normalize.QueryNormalizeContext;
import com.ouroboros.exception.BusinessException;

/** Executes only the model identities and field mappings fixed by the published feature projection. */
public final class BusinessModelFeatureQueryHandler implements BusinessActionHandler {
  private static final QueryNormalizeContext QUERIES = QueryNormalizeContext.builder().withDefaultNormalizers().build();
  private final BusinessModelBehaviorProvider behaviors;

  public BusinessModelFeatureQueryHandler(BusinessModelBehaviorProvider behaviors) { this.behaviors = behaviors; }
  public String getName() { return BusinessActionFeatureQueryPlan.HANDLER_NAME; }

  public Object handle(BusinessActionContext context, BusinessActionInput<?, ?> input) {
    var behavior = behaviors.getBusinessModelBehavior(context.modelFullName()).orElseThrow(() -> new BusinessException("业务模型不可用"));
    var plan = behavior.getFeatureQueryPlans().get(context.actionName());
    if (plan == null) { throw new BusinessException("特性查询尚未完成正式投影：" + context.actionName()); }
    var target = DataModelCenter.getDataModel(plan.queryModel()).orElseThrow(() -> new BusinessException("特性查询数据模型不可用"));
    Map<?, ?> params = input.params() instanceof Map<?, ?> values ? values : Map.of();
    Map<?, ?> query = params.get("query") instanceof Map<?, ?> values ? values : Map.of();
    var raw = new LinkedHashMap<String, Object>();
    for (String clause : List.of("where", "order", "limit", "skip")) {
      if (query.containsKey(clause)) { raw.put(clause, query.get(clause)); }
    }
    raw.put("from", Map.of(target.getFullName(), target.getRawName()));
    var statement = QUERIES.normalizeQuery(raw).getOrElseThrow(error -> new BusinessException("特性查询条件不合法", error));
    SExpression<Boolean> where = statement.getWhere();
    if (!plan.fixedConditions().isEmpty()) {
      where = and(where, QUERIES.forClause("WHERE").normalizeCondition(plan.fixedConditions(), "root")
          .getOrElseThrow(error -> new BusinessException("特性固定范围无法解析", error)));
    }
    if (plan.kind() == BusinessActionFeatureQueryPlan.Kind.HISTORY) {
      where = and(where, SExpression.create(Operators.EQ, SExpression.field(plan.businessKeyField()), SExpression.constant(params.get("recordId"))));
    }
    var fixed = statement.getBuilder().where(where).build();
    var count = QueryStatement.builder().from(statement.getFrom()).where(where).build();
    long total = target.count(count).getOrElseThrow(error -> new BusinessException("特性查询计数失败", error));
    var items = target.query(fixed).getOrElseThrow(error -> new BusinessException("特性查询失败", error));
    return Map.of("items", items, "total", total);
  }

  private static SExpression<Boolean> and(SExpression<Boolean> left, SExpression<Boolean> right) {
    return left == null || left.isEmpty() ? right : SExpression.create(Operators.AND, left, right);
  }
}
