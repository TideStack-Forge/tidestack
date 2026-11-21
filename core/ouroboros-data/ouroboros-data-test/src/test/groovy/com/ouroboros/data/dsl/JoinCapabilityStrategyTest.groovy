package com.ouroboros.data.dsl

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertInstanceOf
import static org.junit.jupiter.api.Assertions.assertTrue

import com.ouroboros.data.AbstractDslIntegrationTest
import com.ouroboros.data.orchestration.JoinCapabilities
import com.ouroboros.data.orchestration.NotJoinableTestCapability
import com.ouroboros.data.orchestration.RelationType
import com.ouroboros.data.orchestration.strategy.DefaultPopulateStrategySelector
import com.ouroboros.data.orchestration.strategy.JoinPopulateStrategy
import com.ouroboros.data.orchestration.strategy.PopulateField
import com.ouroboros.data.orchestration.strategy.SeparatePopulateStrategy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName('JOIN 能力与策略决策集成测试')
class JoinCapabilityStrategyTest extends AbstractDslIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData()
    NotJoinableTestCapability.clear()
  }

  @AfterEach
  void tearDown() {
    NotJoinableTestCapability.clear()
  }

  private PopulateField orderToUserPopulateField() {
    new PopulateField('user', user(), 'userId', 'id', ['id', 'name'], null, order(), RelationType.TO_ONE)
  }

  private PopulateField orderToOrderItemsPopulateField() {
    new PopulateField('orderItems', orderItem(), 'id', 'orderId', ['id', 'price'], null, order(), RelationType.TO_MANY)
  }

  // ── Section 27: JOIN 能力与策略决策 ──

  @Test
  @DisplayName('27-01 同数据站 NATIVE')
  void test_27_01_sameStationNative() {
    def result = JoinCapabilities.canJoin(order(), user())
    assertTrue(result.isJoinable())
  }

  @Test
  @DisplayName('27-02 跨数据站 SEPARATE（SPI 模拟 notJoinable）')
  void test_27_02_crossStationSeparate() {
    NotJoinableTestCapability.markNotJoinable('Order', 'User')

    def result = JoinCapabilities.canJoin(order(), user())
    assertFalse(result.isJoinable())
    assertTrue(result.getReason().contains('test: configured not joinable'))
  }

  @Test
  @DisplayName('27-03 一对多 NATIVE')
  void test_27_03_toManyNative() {
    def result = JoinCapabilities.canJoin(order(), orderItem())
    assertTrue(result.isJoinable())
  }

  @Test
  @DisplayName('27-04 一对多不可 JOIN（SPI 模拟 notJoinable）')
  void test_27_04_toManyNotJoinable() {
    NotJoinableTestCapability.markNotJoinable('Order', 'OrderItem')

    def result = JoinCapabilities.canJoin(order(), orderItem())
    assertFalse(result.isJoinable())
  }

  @Test
  @DisplayName('27-05 Populate JOIN 策略')
  void test_27_05_populateJoinStrategy() {
    def selector = new DefaultPopulateStrategySelector()
    def strategy = selector.select(orderToUserPopulateField())

    assertInstanceOf(JoinPopulateStrategy, strategy)

    def records = queryAndAssertSize(order(), [populate: ['user']], 5)
    assertTrue(records.every { it.get('user') instanceof Map })
  }

  @Test
  @DisplayName('27-06 Populate SEPARATE 策略')
  void test_27_06_populateSeparateStrategy() {
    NotJoinableTestCapability.markNotJoinable('Order', 'User')

    def selector = new DefaultPopulateStrategySelector()
    def strategy = selector.select(orderToUserPopulateField())

    assertInstanceOf(SeparatePopulateStrategy, strategy)

    def records = queryAndAssertSize(order(), [populate: ['user']], 5)
    assertTrue(records.every { it.get('user') instanceof Map })
  }
}
