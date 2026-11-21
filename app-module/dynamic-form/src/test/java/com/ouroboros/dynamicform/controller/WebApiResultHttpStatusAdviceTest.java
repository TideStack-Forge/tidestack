package com.ouroboros.dynamicform.controller;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebApiResultHttpStatusAdviceTest {

  @Test
  void shouldMapExplicitHttpStatusCodesFromWebApiResult() throws Exception {
    var mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
        .setControllerAdvice(new WebApiResultHttpStatusAdvice())
        .build();

    var notFound = mockMvc.perform(get("/status/not-found"))
        .andExpect(status().isNotFound())
        .andReturn()
        .getResponse();
    assertEquals(404, responseCode(notFound.getContentAsString(StandardCharsets.UTF_8)));

    var unauthorized = mockMvc.perform(get("/status/unauthorized"))
        .andExpect(status().isUnauthorized())
        .andReturn()
        .getResponse();
    assertEquals(40103, responseCode(unauthorized.getContentAsString(StandardCharsets.UTF_8)));

    mockMvc.perform(get("/status/success"))
        .andExpect(status().isOk());
  }

  private int responseCode(String body) {
    return Json.toMap(body).containsKey("code") ? ((Number) Json.toMap(body).get("code")).intValue() : -1;
  }

  @RestController
  static class TestController {
    @GetMapping(value = "/status/not-found", produces = MediaType.APPLICATION_JSON_VALUE)
    public WebApiResult<Void> notFound() {
      return WebApiResult.error(404, "not found");
    }

    @GetMapping(value = "/status/unauthorized", produces = MediaType.APPLICATION_JSON_VALUE)
    public WebApiResult<Void> unauthorized() {
      return WebApiResult.error(40103, "unauthorized");
    }

    @GetMapping(value = "/status/success", produces = MediaType.APPLICATION_JSON_VALUE)
    public WebApiResult<Void> success() {
      return WebApiResult.ok();
    }
  }
}
