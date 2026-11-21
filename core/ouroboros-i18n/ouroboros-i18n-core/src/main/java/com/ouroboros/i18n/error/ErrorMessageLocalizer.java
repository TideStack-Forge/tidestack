package com.ouroboros.i18n.error;

import com.ouroboros.errorcode.ResolvedErrorDescriptor;

public interface ErrorMessageLocalizer {

  String localize(String locale, ResolvedErrorDescriptor descriptor);
}
