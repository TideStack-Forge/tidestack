package com.ouroboros.i18n.runtime.extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class StaticMessagesTextExtractorTest {
  @Test
  void enumerationFailureDoesNotBlockAndRetriesBeforeCaching() {
    var reads = new AtomicInteger();
    var extractor = new StaticMessagesTextExtractor(new PathMatchingResourcePatternResolver() {
      @Override
      public Resource[] getResources(String pattern) throws IOException {
        if (reads.incrementAndGet() == 1) {
          throw new IOException("message directory unavailable");
        }
        return new Resource[]{resource("{\"common\":{\"save\":\"Save\"}}")};
      }
    });
    assertTrue(extractor.extract().get().isEmpty());
    assertEquals("Save", extractor.extract().get().getFirst().getSourceText());
    extractor.extract();
    assertEquals(2, reads.get());
  }

  @Test
  void unreadableFileDoesNotHideHealthyMessagesAndRecoversOnRetry() {
    var broken = new java.util.concurrent.atomic.AtomicBoolean(true);
    var extractor = new StaticMessagesTextExtractor(new PathMatchingResourcePatternResolver() {
      @Override
      public Resource[] getResources(String pattern) {
        return new Resource[]{new ByteArrayResource(new byte[0]) {
          @Override
          public String getFilename() {
            return "unreadable.json";
          }

          @Override
          public InputStream getInputStream() throws IOException {
            if (broken.get()) {
              throw new IOException("file unavailable");
            }
            return resource("{\"common\":{\"cancel\":\"Cancel\"}}").getInputStream();
          }
        }, resource("{\"common\":{\"save\":\"Save\"}}")};
      }
    });
    assertEquals("Save", extractor.extract().get().getFirst().getSourceText());
    broken.set(false);
    assertEquals(2, extractor.extract().get().size());
  }

  @Test
  void successfulExtractionDoesNotReadAgainAndReturnsIndependentLists() {
    var reads = new AtomicInteger();
    var extractor = new StaticMessagesTextExtractor(new PathMatchingResourcePatternResolver() {
      @Override
      public Resource[] getResources(String pattern) throws IOException {
        if (reads.incrementAndGet() > 1) {
          throw new IOException("jar unavailable");
        }
        return new Resource[]{resource("{\"common\":{\"save\":\"Save\"}}")};
      }
    });
    extractor.extract().get().clear();

    var second = extractor.extract();

    assertTrue(second.isRight());
    assertEquals("Save", second.get().getFirst().getSourceText());
    assertEquals(1, reads.get());
  }

  @Test
  void partialFailureKeepsHealthyEntriesAndCanRetryCompleteLoad() {
    var broken = new AtomicReference<>("{");
    var extractor = new StaticMessagesTextExtractor(new PathMatchingResourcePatternResolver() {
      @Override
      public Resource[] getResources(String pattern) {
        return new Resource[]{resource("{\"common\":{\"save\":\"Save\"}}"), resource(broken.get())};
      }
    });

    var failure = extractor.extract();

    assertTrue(failure.isRight());
    assertEquals("Save", failure.get().getFirst().getSourceText());
    broken.set("{\"common\":{\"cancel\":\"Cancel\"}}");
    assertEquals(2, extractor.extract().get().size());
  }

  @Test
  void streamIsClosedWhenContentHasBeenLoaded() {
    var closed = new java.util.concurrent.atomic.AtomicBoolean();
    var extractor = new StaticMessagesTextExtractor(new PathMatchingResourcePatternResolver() {
      @Override
      public Resource[] getResources(String pattern) {
        return new Resource[]{new ByteArrayResource("{}".getBytes(StandardCharsets.UTF_8)) {
          @Override
          public String getFilename() {
            return "test.json";
          }

          @Override
          public InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(getByteArray()) {
              @Override
              public void close() throws IOException {
                closed.set(true);
                super.close();
              }
            };
          }
        }};
      }
    });
    assertFalse(closed.get());
    assertTrue(extractor.extract().isRight());
    assertTrue(closed.get());
  }

  private static Resource resource(String text) {
    return new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)) {
      @Override
      public String getFilename() {
        return "test.json";
      }
    };
  }
}
