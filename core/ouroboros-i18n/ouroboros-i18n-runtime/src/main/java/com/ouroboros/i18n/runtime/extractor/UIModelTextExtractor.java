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
import com.ouroboros.uimodel.UIModel;
import com.ouroboros.uimodel.UIModelProvider;
import com.ouroboros.util.SPI;

/**
 * UIModel 文案提取器
 * 提取 UIModel 的 title 和 description 字段
 */
public class UIModelTextExtractor implements TextExtractor {

  private static final Logger logger = LoggerFactory.getLogger(UIModelTextExtractor.class);

  @Override
  public Either<OuroborosException, List<I18nEntry>> extract() {
    List<I18nEntry> entries = new ArrayList<>();

    SPI.getServiceStream(UIModelProvider.class).forEach(provider -> {
      try {
        Map<String, UIModel> models = provider.getUIModelMap();
        if (models == null) {
          return;
        }

        models.forEach((path, model) -> {
          try {
            // 提取 title
            String title = model.getTitle();
            if (title != null && !title.trim().isEmpty()) {
              entries.add(new I18nEntryImpl(
                  path,
                  "title",
                  title,
                  "UIModel: " + path,
                  "title"
              ));
            }

            // 提取 description
            model.getDescription().ifPresent(description -> {
              if (!description.trim().isEmpty()) {
                entries.add(new I18nEntryImpl(
                    path,
                    "description",
                    description,
                    "UIModel: " + path,
                    "description"
                ));
              }
            });
          } catch (Exception e) {
            logger.error("Failed to extract text from UIModel: {}", path, e);
          }
        });
      } catch (Exception e) {
        logger.error("Failed to extract from UIModelProvider: {}", provider.getClass().getName(), e);
      }
    });

    logger.debug("Extracted {} UIModel text entries", entries.size());
    return Either.right(entries);
  }
}
