package com.ouroboros.data.dict.service.impl;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.data.dict.pojo.FlatDataDict;
import com.ouroboros.data.dict.pojo.FlatDataDictItem;
import com.ouroboros.data.dict.service.DataDictCoreService;
import com.ouroboros.data.dict.service.DataDictItemService;
import com.ouroboros.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataDictServiceImplTest {

  private DataDictCoreService dataDictCoreService;
  private DataDictItemService dataDictItemService;
  private DataDictServiceImpl service;

  @BeforeEach
  void setUp() {
    dataDictCoreService = mock(DataDictCoreService.class);
    dataDictItemService = mock(DataDictItemService.class);
    service = new DataDictServiceImpl();
    ReflectionTestUtils.setField(service, "self", service);
    ReflectionTestUtils.setField(service, "dataDictCoreService", dataDictCoreService);
    ReflectionTestUtils.setField(service, "dataDictItemService", dataDictItemService);
  }

  @Test
  void addItemsShouldUse404WhenDictionaryMissing() {
    when(dataDictCoreService.getByName("missing")).thenReturn(Optional.empty());

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.addItems("missing", Collections.singletonList(item("missing", "code-1")))
    );

    assertEquals(Integer.valueOf(404), exception.getErrorCode());
    assertEquals("字典missing不存在", exception.getMessage());
  }

  @Test
  void updateItemShouldUse404WhenItemMissing() {
    FlatDataDict model = new FlatDataDict();
    model.setName("dict");
    model.setEditable(true);
    doReturn(Optional.of(model)).when(dataDictCoreService).getByName("dict");
    when(dataDictItemService.getByDictCode("dict", "missing")).thenReturn(Optional.empty());

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.updateItem("dict", "missing", item("dict", "missing"))
    );

    assertEquals(Integer.valueOf(404), exception.getErrorCode());
    assertEquals("字典项missing不存在", exception.getMessage());
  }

  private FlatDataDictItem item(String dict, String code) {
    FlatDataDictItem item = new FlatDataDictItem();
    item.setDict(dict);
    item.setCode(code);
    item.setLabel(code);
    item.setOrder(1);
    return item;
  }
}
