package com.ouroboros.i18n.runtime.extractor;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Either;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.i18n.extractor.TextExtractor;
import com.ouroboros.i18n.model.I18nEntry;
import com.ouroboros.i18n.runtime.model.I18nEntryImpl;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.util.SPI;

/**
 * 权限文案提取器
 */
public class AuthorityTextExtractor implements TextExtractor {

  private static final Logger logger = LoggerFactory.getLogger(AuthorityTextExtractor.class);
  private static final String NAMESPACE = "authority";
  private static final String PROPERTY_NAME = "title";

  @Override
  public Either<OuroborosException, List<I18nEntry>> extract() {
    List<I18nEntry> entries = new ArrayList<>();

    SPI.getServiceStream(AuthorityProvider.class).forEach(provider -> {
      try {
        List<Authority> authorities = provider.getAllAuthorities();
        if (authorities == null) {
          logger.warn("AuthorityProvider.getAllAuthorities() returned null: {}", provider.getClass().getName());
          return;
        }

        authorities.forEach(authority -> {
          String fullName = authority.getFullName();
          String title = authority.getTitle();

          if (title != null && !title.trim().isEmpty()) {
            I18nEntry entry = new I18nEntryImpl(
                NAMESPACE,
                fullName + "." + PROPERTY_NAME,
                title,
                "Authority: " + fullName,
                PROPERTY_NAME
            );
            entries.add(entry);
          }
        });
      } catch (Exception e) {
        logger.error("Failed to extract authority texts from provider: {}", provider.getClass().getName(), e);
      }
    });

    logger.debug("Extracted {} authority text entries", entries.size());
    return Either.right(entries);
  }
}
