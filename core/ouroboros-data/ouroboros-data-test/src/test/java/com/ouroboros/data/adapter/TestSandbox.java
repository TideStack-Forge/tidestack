package com.ouroboros.data.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.TestApplication;
import com.querydsl.core.DefaultQueryMetadata;
import com.querydsl.core.QueryMetadata;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.alias.DefaultPathFactory;
import com.querydsl.core.alias.PathFactory;
import com.querydsl.core.support.QueryMixin;
import com.querydsl.core.types.*;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.sql.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
public class TestSandbox {

  @Autowired
  JdbcTemplate jdbcTemplate;
  @Autowired
  private DataSource dataSource;
  @Autowired
  private Configuration configuration;
  @Autowired
  private SQLQueryFactory queryFactory;
  private SimpleExpression<Object> parentName;

  @Test
  @Transactional
  public void testQueryByQClass() {
    // QUser user = QUser.user;

    // Path
    var userPath = Expressions.simplePath(Object.class, "t_user");
    var userNamePath = Expressions.stringPath(userPath, "name");
    var select = SQLExpressions.all;

    // Expression < Path
    // SQL statement
    // DQL:

    // 在QueryDSL里，如何看待一个 SQL 语句: Statement -> Clause -> Expression
    // Expression:
    //    - Path
    //    - Operation -> Operator + Expression
    // AST
    //
    // select *
    // from user
    // where name like 'Jerry%'

    // ?name$$like=Jerry
    // Map<String, String> -> {
    //   "name$$like": "Jerry"
    //   "id$$gt": 1
    // }
    // SQL Where Clause
    //  name like '%Jerry%' AND id > 1
    // SELECT Clause

    // name
    var namePredicate = Expressions.stringPath("name").like("Jerry");
    var idPredicate = Expressions.numberPath(Integer.class, "id").gt(100);

    var combinePredicate = Expressions.allOf(namePredicate, idPredicate);

    var query = queryFactory.select(select)
        .from(userPath)
        .where(combinePredicate); // => SQL: where name like 'Jerry%'

    var count = query.fetch();
    System.out.println(count);
  }

  @Test
  @Transactional
  @SuppressWarnings("unchecked")
  public void testQueryByManualCreatePath() {
    Path<Object> table = new RelationalPathBase<>(Object.class, "u", "", "t_user");
    PathFactory pathFactory = new DefaultPathFactory();
    PathMetadata idMeta = PathMetadataFactory.forProperty(table, "id");
    PathMetadata nameMeta = PathMetadataFactory.forProperty(table, "name");
    Path<String>[] fields = new Path[]{pathFactory.createSimplePath(String.class, idMeta),
        pathFactory.createStringPath(nameMeta)};
    QueryResults<Tuple> users = queryFactory
        .select(fields)
        .from(table)
        .where(Expressions.booleanOperation(Ops.GOE, fields[0], Expressions.constant(1))).fetchResults();
    System.out.println(users.isEmpty());
  }

  @Test
  @Transactional
  @SuppressWarnings("unchecked")
  public void testQueryByQueryMeta() throws SQLException {
    QueryMetadata queryMetadata = new DefaultQueryMetadata();
    QueryMixin<Object> queryMixin = new QueryMixin<>(queryMetadata);

    PathFactory pathFactory = new DefaultPathFactory();
    RelationalPath<Object> table = new RelationalPathBase<>(Object.class, "u", "", "t_user");
    PathMetadata idMeta = PathMetadataFactory.forProperty(table, "id");
    PathMetadata nameMeta = PathMetadataFactory.forProperty(table, "name");

    Path<Object>[] fields = new Path[]{pathFactory.createNumberPath(Integer.class, idMeta),
        pathFactory.createStringPath(nameMeta)};
    queryMixin.setProjection(SQLExpressions.all);
    queryMixin.from(table);
    queryMixin.where(Expressions.booleanOperation(Ops.GOE, fields[0], Expressions.constant(1)));
    SQLQuery<Object> query = new SQLQuery<>(dataSource.getConnection(), configuration, queryMetadata);
    List<?> result = query.fetch();
    System.out.println(result.size());
  }

  @Test
  @Transactional
  public void testTemplateUpdate() throws Exception {
    var keyHolder = new GeneratedKeyHolder();
    var stmCreator = new PreparedStatementCreatorFactory("update `t_user` set `name` = 'Jerry'");
    stmCreator.setReturnGeneratedKeys(true);
    var effectedRows = jdbcTemplate.update(stmCreator.newPreparedStatementCreator(Collections.emptyList()), keyHolder);
    System.out.println(effectedRows);
    System.out.println(String.valueOf(keyHolder.getKey()));

  }

  @Test
  public void testFunctionComposition() {
    var key = "EQ";
    var op = Arrays.asList(
            Ops::valueOf,
            Ops.AggOps::valueOf,
            Ops.MathOps::valueOf,
            Ops.StringOps::valueOf,
            Ops.DateTimeOps::valueOf,
            (Function<String, Operator>) Ops.QuantOps::valueOf)
        .stream()
        .reduce((Operator) null, (acc, curr) -> acc == null ? curr.apply(key) : acc, (a, b) -> a);
    assertEquals(op, Ops.EQ);
  }

  @Test
  public void testPattern() {
    var pattern = Pattern.compile("([a-zA-Z]+)(\\((?:\\d+)(?:\\s*,\\d+)?\\))?\\s+(unsigned|UNSIGNED)?");
    var test1 = "varchar(200,2) UNSIGNED";
    var matcher = pattern.matcher(test1);
    assertTrue(matcher.matches());
    for (int i = 0; i <= matcher.groupCount(); i++) {
      System.out.println(matcher.group(i));
    }
  }

  public class User {
    private Integer id;
    private String name;
    private Integer parent;

    public Integer getId() {
      return id;
    }

    public void setId(Integer id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Integer getParent() {
      return parent;
    }

    public void setParent(Integer parent) {
      this.parent = parent;
    }
  }
}
