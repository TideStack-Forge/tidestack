package com.xinpai.ouroboros.compatible;

import com.xinpai.common.groovy.Groson;
import com.xinpai.common.jackson.JsonObject;
import com.xinpai.common.model.SearchResultModel;
import com.xinpai.ouroboros.data.adapter.SqlDataAdapter;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author liansz
 * @createTime 2023/3/31 16:06
 */
@Component("csda")
public class CompatibleSqlDataAdapter extends SqlDataAdapter {

  private final OrElseThrow orElseThrow;

  public CompatibleSqlDataAdapter(DataSource dataSource) {
    super(dataSource);
    orElseThrow = new OrElseThrow(this);
  }

  /**
   * 增加异常抛出扩展
   */
  public static class OrElseThrow {

    private final CompatibleSqlDataAdapter adapter;

    public OrElseThrow(CompatibleSqlDataAdapter adapter) {
      this.adapter = adapter;
    }

    /**
     * 查询列表数据
     *
     * @param queryMap
     * @return
     */
    public List<Groson> queryList(Map<String, Object> queryMap) {
      return adapter.query(queryMap).getOrElseThrow(e -> e).stream()
          .map(record -> new Groson(new JsonObject(record)))
          .collect(Collectors.toList());
    }

    /**
     * 查询分页数据
     *
     * @param queryMap
     * @param page
     * @param limit
     * @return
     */
    public SearchResultModel<Groson> queryPaging(Map<String, Object> queryMap, int page, int limit) {
      // 查询数据添加分页信息
      queryMap.put("skip", (page - 1) * limit);
      queryMap.put("limit", limit);

      SearchResultModel<Groson> result = new SearchResultModel<>();

      result.setPage(page);
      result.setLimit(limit);

      Long count = adapter.count(queryMap).getOrElseThrow(e -> e);
      result.setTotalCount(count.intValue());

      List<Groson> list = count > 0 ? queryList(queryMap) : new ArrayList<>();
      result.setList(list);

      return result;
    }
  }

  public OrElseThrow getOrElseThrow() {
    return orElseThrow;
  }
}
