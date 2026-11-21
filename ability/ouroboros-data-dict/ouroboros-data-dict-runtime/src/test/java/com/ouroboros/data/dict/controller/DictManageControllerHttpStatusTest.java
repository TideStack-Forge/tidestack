package com.ouroboros.data.dict.controller;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ouroboros.data.dict.DataDictService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DictManageControllerHttpStatusTest {

  @Test
  void missingDictionaryShouldReturnHttp404() throws Exception {
    DataDictService service = mock(DataDictService.class);
    when(service.detail("missing")).thenReturn(Optional.empty());

    DictManageController controller = new DictManageController();
    ReflectionTestUtils.setField(controller, "dataDictService", service);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/sys/data-dict-manage/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("数据字典不存在"));
  }

  @Test
  void updateMissingItemShouldReturnHttp404() throws Exception {
    DataDictService service = mock(DataDictService.class);
    when(service.updateItem(eq("dict"), eq("missing"), any())).thenReturn(false);

    DictManageController controller = new DictManageController();
    ReflectionTestUtils.setField(controller, "dataDictService", service);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/sys/data-dict-manage/dict/item/missing")
            .contentType("application/json")
            .content("{\"code\":\"missing\",\"label\":\"Missing\",\"order\":1}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("字典项不存在"));
  }

  @RestControllerAdvice
  static class BusinessExceptionAdvice {
    @ExceptionHandler(BusinessException.class)
    public WebApiResult<?> handle(BusinessException exception) {
      return WebApiResult.error(exception.getErrorCode(), exception.getMessage());
    }
  }
}
