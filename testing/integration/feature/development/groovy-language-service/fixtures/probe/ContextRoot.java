package probe;

import java.util.List;

public final class ContextRoot {
  public Customer getCustomer() { return new Customer(); }
  public List<Customer> getCustomers() { return List.of(new Customer()); }
}
