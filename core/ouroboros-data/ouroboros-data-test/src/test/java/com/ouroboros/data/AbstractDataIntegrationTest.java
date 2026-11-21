package com.ouroboros.data;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.fixture.TestDataFixture;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.TestModels;
import com.ouroboros.data.station.DataStation;

/**
 * 集成测试基类
 * <p>
 * 提供测试所需的基础设施：
 * <ul>
 *   <li>DataStation 和 DataSource 自动注入</li>
 *   <li>TestDataFixture 用于数据初始化</li>
 *   <li>便捷的模型获取方法</li>
 *   <li>@Transactional 保证测试隔离（自动回滚）</li>
 * </ul>
 *
 * @since 1.0.0-beta.2
 */
@SpringBootTest(classes = TestApplication.class)
@Transactional
public abstract class AbstractDataIntegrationTest {

  @Autowired
  protected DataStation<?> testDataStation;

  @Autowired
  protected DataSource dataSource;

  protected TestDataFixture fixture;

  @BeforeEach
  void setUpBase() {
    fixture = new TestDataFixture(testDataStation);
  }

  /**
   * 获取指定名称的数据模型
   *
   * @param name 模型名称
   * @return 数据模型
   * @throws IllegalArgumentException 如果模型不存在
   */
  protected DataModel getModel(String name) {
    return testDataStation.getDataModel(name)
        .orElseThrow(() -> new IllegalArgumentException("Model not found: " + name));
  }

  /**
   * 获取部门模型
   */
  protected DataModel department() {
    return getModel(TestModels.DEPARTMENT);
  }

  /**
   * 获取用户模型
   */
  protected DataModel user() {
    return getModel(TestModels.USER);
  }

  /**
   * 获取订单模型
   */
  protected DataModel order() {
    return getModel(TestModels.ORDER);
  }
}
