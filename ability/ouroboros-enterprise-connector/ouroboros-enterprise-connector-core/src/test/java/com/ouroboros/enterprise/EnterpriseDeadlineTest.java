package com.ouroboros.enterprise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

class EnterpriseDeadlineTest {
  @Test
  void expiresAndCancelsARequestWithinTheOverallBudget() throws Exception {
    var interrupted = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    var deadline = EnterpriseDeadline.afterMillis(200);
    assertThatThrownBy(() -> deadline.execute(() -> {
      started.countDown();
      try {
        new CountDownLatch(1).await();
        return "unreachable";
      } catch (InterruptedException exception) {
        interrupted.countDown();
        throw exception;
      }
    })).isInstanceOf(TimeoutException.class);
    assertThat(started.getCount()).isZero();
    assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
    assertThatThrownBy(() -> deadline.execute(() -> "second request"))
        .isInstanceOf(TimeoutException.class);
  }

  @Test
  void returnsResultsAndPreservesFailure() {
    var deadline = EnterpriseDeadline.afterMillis(1000);
    assertThatThrownBy(() -> deadline.execute(() -> { throw new IllegalStateException("failed"); }))
        .isInstanceOf(IllegalStateException.class).hasMessage("failed");
    assertThatThrownBy(() -> EnterpriseDeadline.afterMillis(0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
