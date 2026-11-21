package com.ouroboros.data

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

import com.ouroboros.data.model.DataModel
import com.ouroboros.data.model.TestModels
import com.ouroboros.data.record.RecordList

abstract class AbstractDslIntegrationTest extends AbstractDataIntegrationTest {

  protected DataModel orderItem() {
    getModel(TestModels.ORDER_ITEM)
  }

  protected DataModel review() {
    getModel(TestModels.REVIEW)
  }

  protected RecordList query(DataModel model, Map dsl) {
    model.query(dsl).get()
  }

  protected RecordList queryAndAssertSize(DataModel model, Map dsl, int expectedSize) {
    def result = query(model, dsl)
    assertEquals(expectedSize, result.size(), "查询 ${dsl} 期望 ${expectedSize} 条，实际 ${result.size()} 条")
    result
  }

  protected void assertQueryFails(DataModel model, Map dsl) {
    def result = model.query(dsl)
    assertTrue(result.isFailure(), "查询 ${dsl} 期望失败但成功了")
  }

  protected void assertQueryFailsWith(DataModel model, Map dsl, String messageContains) {
    def result = model.query(dsl)
    assertTrue(result.isFailure(), "查询 ${dsl} 期望失败但成功了")
    def cause = result.getCause()
    def message = cause == null ? "" : String.valueOf(cause.getMessage())
    assertTrue(message.contains(messageContains), "错误消息期望含 '${messageContains}'，实际: ${message}")
  }
}
