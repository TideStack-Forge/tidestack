package com.ouroboros.data.orchestration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.AbstractDataIntegrationTest;
import com.ouroboros.data.adapter.OuroborosSQLQuery;
import com.ouroboros.data.dsl.ModelFieldPath;
import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.data.dsl.Operators;
import com.ouroboros.data.dsl.SExpression;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.data.dsl.statement.ModelQueryStatementBuilder;
import com.ouroboros.data.model.DefaultDataModel;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.analyze.DefaultQueryAnalyzer;
import com.ouroboros.data.analyze.DefaultQueryTransformer;
import com.ouroboros.data.analyze.OptimizationAnalyzer;
import com.ouroboros.data.analyze.QueryAnalyzeContext;
import com.ouroboros.data.analyze.WildcardExpandAnalyzer;
import com.ouroboros.data.normalize.QueryNormalizeContext;
import com.ouroboros.data.normalize.normalizers.PopulateOmitNormalizer;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.data.sql.SqlTemplatesFactory;
import com.ouroboros.data.transpile.BaseTranspileContext;
import com.ouroboros.data.transpile.FieldSource;
import com.querydsl.core.types.Path;
import com.querydsl.sql.RelationalPathBase;

@DisplayName("自动回查 SQL 生成集成测试")
class AutoGetSqlGenerationIntegrationTest extends AbstractDataIntegrationTest {

  @Test
  @DisplayName("默认 get(id) 查询生成的 SELECT 应该保留原始列名")
  void autoGetQuery_shouldKeepRawColumnNames() {
    DataModel userModel = user();

    Map<String, Object> queryMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    queryMap.put(Keyword.FROM.toString(),
        Collections.singletonMap(userModel.getFullName(), userModel.getRawName()));
    queryMap.put(Keyword.WHERE.toString(), Collections.singletonMap("id", 1L));

    QueryNormalizeContext normalizeContext = QueryNormalizeContext.builder()
        .withDefaultNormalizers()
        .addClauseNormalizer(new PopulateOmitNormalizer())
        .build();

    var normalized = normalizeContext.normalizeQuery(queryMap, new ModelQueryStatementBuilder()).get();
    QueryAnalyzeContext analyzeContext = QueryAnalyzeContext.builder()
        .model(userModel)
        .build();
    var transformedBeforeAnalyze = new DefaultQueryTransformer(Collections.singletonList(new WildcardExpandAnalyzer()))
        .transform(normalized, analyzeContext)
        .get();
    var analyzed = new DefaultQueryAnalyzer().analyze(transformedBeforeAnalyze, analyzeContext).get();
    var transformedAfterAnalyze = new DefaultQueryTransformer(Collections.singletonList(new OptimizationAnalyzer()))
        .transform(analyzed, analyzeContext)
        .get();

    DefaultQueryOrchestrator orchestrator = new DefaultQueryOrchestrator();
    final String[] generatedSql = new String[1];
    var result = orchestrator.orchestrate(
        transformedAfterAnalyze,
        userModel,
        statement -> {
          generatedSql[0] = new OuroborosSQLQuery(
              SqlTemplatesFactory.getSQLTemplates(dataSource, null),
              new com.ouroboros.data.transpile.DefaultQueryTranspiler()
                  .applyWithContext(statement, createContext(userModel))
                  .get())
              .getSQL()
              .getSQL();
          return io.vavr.control.Try.success(RecordList.empty());
        },
        new OrchestrationContext());

    assertTrue(result.isSuccess(), generatedSql[0]);
    assertTrue(generatedSql[0].contains("department_id"), generatedSql[0]);
    assertTrue(generatedSql[0].contains("created_at"), generatedSql[0]);
  }

  @Test
  @DisplayName("模型级 QueryStatement 查询应该使用模型字段原始列名")
  void modelQueryStatement_shouldUseModelFieldRawNames() {
    DataModel userModel = user();
    QueryStatement statement = QueryStatement.builder()
        .from(userModel.getRawName(), userModel.getFullName())
        .select(SExpression.create(Operators.COLUMNS, "*"))
        .where(SExpression.create(
            Operators.EQ,
            SExpression.field("id"),
            SExpression.constant(1L)))
        .build();

    var result = userModel.query(statement);

    assertTrue(result.isSuccess(), result.isFailure() ? result.getCause().getMessage() : "");
  }

  private BaseTranspileContext createContext(DataModel model) {
    Path<?> tablePath = new RelationalPathBase<>(Object.class, model.getRawName(), "", model.getRawName());
    FieldSource fieldSource = new FieldSource() {
      @Override
      public Optional<Path<?>> getField(String fieldName) {
        return model.getField(fieldName)
            .map(field -> ModelFieldPath.of(Object.class, field));
      }

      @Override
      public List<Path<?>> getFields() {
        return model.getFields().stream()
            .map(field -> (Path<?>) ModelFieldPath.of(Object.class, field))
            .collect(Collectors.toList());
      }

      @Override
      public Path<?> getSelfPath() {
        return tablePath;
      }
    };
    return new BaseTranspileContext(fieldSource, model.getFullName());
  }
}
