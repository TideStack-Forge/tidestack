package probe;

public final class Customer {
  public String getName() { return "Alice"; }
  public boolean isActive() { return true; }
  public Address getAddress() { return new Address(); }

  public static final class Address {
    public String getCity() { return "Shanghai"; }
  }
}
