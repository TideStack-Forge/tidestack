package com.xinpai.spreadsheet;

import com.xinpai.spreadsheet.db.ISpreadsheetDAO;
import com.xinpai.spreadsheet.db.entity.SpreadsheetModel;
import com.xinpai.spreadsheet.utils.SnowFlake;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@RunWith(SpringRunner.class)
public class TestSpreadsheetDAO {

  @Autowired
  ISpreadsheetDAO spreadsheetDAO;

  @Autowired
  SnowFlake snowFlake;

  @Before
  public void initTestData() {
    spreadsheetDAO.delete("TEST_SPREADSHEET_ID");
    spreadsheetDAO.delete("NOT_EXIST_ID");
    // 创建spreadsheet
    SpreadsheetModel spreadsheet = new SpreadsheetModel();
    spreadsheet.setId("TEST_SPREADSHEET_ID");
    spreadsheet.setApp_id("App_Id");
    spreadsheet.setName("test");
    spreadsheet.setDescription("desc");
    spreadsheetDAO.add(spreadsheet);
  }

  @After
  public void cleanTestDate() {
    spreadsheetDAO.delete("TEST_SPREADSHEET_ID");
  }

  @Test
  @Transactional
  public void testAdd() {
    SpreadsheetModel spreadsheet = new SpreadsheetModel();
    spreadsheet.setName("test");
    spreadsheet.setDescription("desc");
    spreadsheet.setApp_id("app_id");
    spreadsheet.setIs_deleted(0);
    spreadsheet.setId(snowFlake.nextIdHex());

    Boolean result = spreadsheetDAO.add(spreadsheet);
    Assert.assertTrue("添加数据成功应返回true", result);

    spreadsheet = spreadsheetDAO.getById(spreadsheet.getId());
    Assert.assertNotNull("查找刚添加的数据不能为null", spreadsheet);
  }

  @Test
  public void testGetById() {
    SpreadsheetModel spreadsheet = spreadsheetDAO.getById("NOT_EXIST_ID");
    Assert.assertNull("查找不存在的ID时返回null", spreadsheet);

    spreadsheet = spreadsheetDAO.getById("TEST_SPREADSHEET_ID");
    Assert.assertNotNull("查找存在的ID时不能返回null", spreadsheet);
  }

  @Test
  @Transactional
  public void testUpdate() {
    SpreadsheetModel spreadsheet = spreadsheetDAO.getById("TEST_SPREADSHEET_ID");
    spreadsheet.setIs_deleted(1);
    spreadsheetDAO.update(spreadsheet);
    SpreadsheetModel spreadsheet2 = spreadsheetDAO.getById(spreadsheet.getId());
    Assert.assertTrue("检查更新结果", spreadsheet2.getIs_deleted() == 1);
  }

  @Test
  @Transactional
  public void testDelete() {
    Boolean delNotExistData = spreadsheetDAO.delete("NOT_EXIST_ID");
    Assert.assertFalse("删除不存在的数据返回失败", delNotExistData);

    SpreadsheetModel spreadsheet = spreadsheetDAO.getById("TEST_SPREADSHEET_ID");
    Assert.assertNotNull("确认要删除的对象存在", spreadsheet);
    spreadsheetDAO.delete(spreadsheet.getId());
    spreadsheet = spreadsheetDAO.getById("TEST_SPREADSHEET_ID");
    Assert.assertNull("确认要删除的对象已删除", spreadsheet);
  }

}
