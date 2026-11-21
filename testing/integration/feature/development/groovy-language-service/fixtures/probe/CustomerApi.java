package probe;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import java.util.List;

public final class CustomerApi {
  public static List<Customer> load() { return List.of(new Customer()); }

  public static String withBuilder(
      @DelegatesTo(value = Builder.class, strategy = Closure.DELEGATE_FIRST) Closure<?> block) {
    var builder = new Builder();
    block.setDelegate(builder);
    block.setResolveStrategy(Closure.DELEGATE_FIRST);
    block.call();
    return builder.getTitle();
  }

  public static final class Builder {
    public String getTitle() { return "title"; }
    public void setTitle(String title) { /* Semantic fixture; execution is outside this scenario. */ }
    public Builder appendTitle(String suffix) { return this; }
  }
}
