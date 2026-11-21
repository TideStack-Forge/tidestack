package com.ouroboros.businessmodel.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.businessmodel.transaction.BusinessTransactionResources;
import com.ouroboros.data.adapter.SqlDataAdapter;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.exception.BusinessException;

class BusinessTransactionResourcesTest {
  @Test
  void verifiedModelWritesAndRollbackUseTheOwningCommitConnection() {
    var source = source();
    var manager = new DataSourceTransactionManager(source);
    var adapter = new SqlDataAdapter(source, "H2");
    var model = model(adapter);
    var jdbc = new JdbcTemplate(source);
    jdbc.execute("CREATE TABLE effects (id INT PRIMARY KEY)");
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      var guard = BusinessTransactionResources.capture(manager, status);
      guard.beforeWrite(model);
      adapter.execute("INSERT INTO effects VALUES (?)", 1).get();
      status.setRollbackOnly();
    });
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM effects", Integer.class));
  }

  @Test
  void anUnenlistedResourceOrIndependentNestedTransactionCannotWrite() {
    var source = source();
    var manager = new DataSourceTransactionManager(source);
    var unrelated = new SqlDataAdapter(source(), "H2");
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      var guard = BusinessTransactionResources.capture(manager, status);
      assertThrows(BusinessException.class, () -> guard.beforeWrite(model(unrelated)));
    });
    var adapter = new SqlDataAdapter(source, "H2");
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      var guard = BusinessTransactionResources.capture(manager, status);
      var nested = new TransactionTemplate(manager);
      nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      nested.executeWithoutResult(inner -> assertThrows(BusinessException.class,
          () -> guard.beforeWrite(model(adapter))));
    });
  }

  @Test
  void transactionPresenceWithoutAResourceProofIsRejected() {
    var manager = new BusinessEventTestSupport.Transactions();
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      var guard = BusinessTransactionResources.capture(manager, status);
      assertThrows(BusinessException.class, () -> guard.beforeWrite(model(new SqlDataAdapter(source(), "H2"))));
    });
  }

  private DataModel model(SqlDataAdapter adapter) {
    var model = mock(DataModel.class);
    when(model.getAdapter()).thenReturn(adapter);
    when(model.getFullName()).thenReturn("sales.Order");
    return model;
  }

  private JdbcDataSource source() {
    var source = new JdbcDataSource();
    source.setURL("jdbc:h2:mem:participation_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    source.setUser("sa");
    return source;
  }
}
