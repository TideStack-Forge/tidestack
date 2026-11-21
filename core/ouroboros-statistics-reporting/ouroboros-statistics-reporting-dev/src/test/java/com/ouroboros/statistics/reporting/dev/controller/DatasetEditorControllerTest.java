package com.ouroboros.statistics.reporting.dev.controller;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.statistics.reporting.dev.service.DatasetEditorService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DatasetEditorControllerTest {
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    DatasetEditorService datasetEditorService = (appName, datasetName) -> Optional.empty();
    mockMvc = MockMvcBuilders.standaloneSetup(new DatasetEditorController(datasetEditorService)).build();
  }

  @Test
  void shellEndpoint_shouldReturnNotFoundForMissingDataset() throws Exception {
    mockMvc.perform(get("/api/dev/demo-app/dataset/demo.missing/editor"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(-1));
  }
}
