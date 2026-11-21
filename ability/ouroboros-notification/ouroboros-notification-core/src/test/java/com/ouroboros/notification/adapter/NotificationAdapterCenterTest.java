package com.ouroboros.notification.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import com.ouroboros.util.SPI;

class NotificationAdapterCenterTest {
  @Test
  void descriptorsBindOnlyToTheCurrentContainerWithoutRetainingPreviousServices() {
    var firstAdapter = mock(NotificationAdapter.class);
    var secondAdapter = mock(NotificationAdapter.class);
    var descriptor = new Descriptor();
    try (var spi = mockStatic(SPI.class)) {
      spi.when(() -> SPI.getCachedSortedServiceStream(NotificationAdapterSupplier.class))
          .thenAnswer(call -> Stream.of(descriptor));
      assertThat(NotificationAdapterCenter.findSupplier("managed").orElseThrow()).isSameAs(descriptor);
      assertThat(NotificationAdapterCenter.getAllAdapter()).isEmpty();
      assertThat(NotificationAdapterCenter.getAllAdapter(List.of(new Managed(firstAdapter))).get("managed")).isSameAs(firstAdapter);
      assertThat(NotificationAdapterCenter.getAllAdapter(List.of(new Managed(secondAdapter))).get("managed")).isSameAs(secondAdapter);
      assertThatThrownBy(() -> NotificationAdapterCenter.getAllSuppliers(List.of())).hasMessageContaining("装配缺失或重复");
      assertThatThrownBy(() -> NotificationAdapterCenter.getAllSuppliers(List.of(new Managed(firstAdapter), new Managed(secondAdapter))))
          .hasMessageContaining("装配缺失或重复");
      verifyNoInteractions(firstAdapter, secondAdapter);
    }
  }

  private static final class Descriptor implements NotificationAdapterSupplier {
    @Override public String getName() { return "managed"; }
    @Override public Optional<Class<? extends NotificationAdapterSupplier>> managedSupplierType() { return Optional.of(Managed.class); }
    @Override public <D extends NotificationChannelSchema, O extends NotificationChannelOptions> NotificationAdapter<D, O> getAdapter() {
      throw new IllegalStateException("descriptor cannot send");
    }
  }

  private record Managed(NotificationAdapter<?, ?> adapter) implements NotificationAdapterSupplier {
    @Override public String getName() { return "managed"; }
    @Override public <D extends NotificationChannelSchema, O extends NotificationChannelOptions> NotificationAdapter<D, O> getAdapter() {
      return (NotificationAdapter<D, O>) adapter;
    }
  }
}
