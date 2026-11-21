package com.ouroboros.security.authorize;

import jakarta.servlet.Filter;

public interface AuthorizeFilterSupplier {

  Filter getAuthorizeFilter();
}
