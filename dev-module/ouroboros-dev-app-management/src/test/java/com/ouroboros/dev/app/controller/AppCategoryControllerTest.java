package com.ouroboros.dev.app.controller;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.dev.app.service.AppCategoryService;

import io.vavr.control.Try;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppCategoryControllerTest {

  private final AppCategoryService appCategoryService = mock(AppCategoryService.class);

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    AppCategoryController controller = new AppCategoryController();
    ReflectionTestUtils.setField(controller, "appCategoryService", appCategoryService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void listCategoriesHttpEndpointAcceptsEmptyExceptParam() throws Exception {
    when(appCategoryService.listCategories()).thenReturn(Try.success(Collections.emptyList()));

    mockMvc.perform(get("/api/app/category").param("except", ""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.items.length()").value(0));
  }
}
