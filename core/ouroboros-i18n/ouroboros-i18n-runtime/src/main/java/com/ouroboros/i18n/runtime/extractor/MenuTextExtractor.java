package com.ouroboros.i18n.runtime.extractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Either;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.i18n.extractor.TextExtractor;
import com.ouroboros.i18n.model.I18nEntry;
import com.ouroboros.i18n.runtime.model.I18nEntryImpl;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.MenuModelProvider;
import com.ouroboros.util.SPI;

/**
 * 菜单文案提取器
 */
public class MenuTextExtractor implements TextExtractor {

  private static final Logger logger = LoggerFactory.getLogger(MenuTextExtractor.class);
  private static final String NAMESPACE = "menu";
  private static final String PROPERTY_NAME = "title";

  @Override
  public Either<OuroborosException, List<I18nEntry>> extract() {
    List<I18nEntry> entries = new ArrayList<>();

    SPI.getServiceStream(MenuModelProvider.class).forEach(provider -> {
      try {
        Map<String, MenuModel> menus = provider.getAllMenus();
        if (menus == null) {
          logger.warn("MenuModelProvider.getAllMenus() returned null: {}", provider.getClass().getName());
          return;
        }

        menus.values().forEach(menu -> {
          String fullName = menu.getFullName();
          String title = menu.getTitle();

          if (title != null && !title.trim().isEmpty()) {
            I18nEntry entry = new I18nEntryImpl(
                NAMESPACE,
                fullName + "." + PROPERTY_NAME,
                title,
                "MenuModel: " + fullName,
                PROPERTY_NAME
            );
            entries.add(entry);
          }
        });
      } catch (Exception e) {
        logger.error("Failed to extract menu texts from provider: {}", provider.getClass().getName(), e);
      }
    });

    logger.debug("Extracted {} menu text entries", entries.size());
    return Either.right(entries);
  }
}
