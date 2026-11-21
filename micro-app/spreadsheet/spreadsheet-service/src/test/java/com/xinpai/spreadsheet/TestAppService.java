package com.xinpai.spreadsheet;

import java.util.List;

import com.xinpai.spreadsheet.db.entity.AppModel;
import com.xinpai.spreadsheet.db.handler.IAppDao;
import com.xinpai.spreadsheet.service.AppService;

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
public class TestAppService {
  @Autowired
  IAppDao appDao;

  @Autowired
  AppService appService;

  @Before
  public void initTestData() {
    appDao.delete("NOT_EXIST_ID");
    appDao.delete("TEST_APP_ID");

    AppModel app = new AppModel();
    app.setId("TEST_APP_ID");
    app.setDescription("desc");
    app.setName("name");
    app.setStatus("status");
    appDao.add(app);
  }

  @After
  public void cleanTestDate() {
    appDao.delete("TEST_APP_ID");
  }

  @Test
  public void testGetById() {
    AppModel app = appDao.getById("NOT_EXIST_ID");
    Assert.assertNull("查找不存在的app应返回null", app);

    app = appDao.getById("TEST_APP_ID");
    Assert.assertNotNull("查找已存在的app不能为空", app);
  }

  @Test
  @Transactional
  public void testAdd() {
    AppModel app = new AppModel();
    app.setName("test2");
    List<String> result = appService.add(app);

    Assert.assertFalse("创建app后应返回非null", result == null || result.size() != 2);
  }

  @Test
  public void testUpdate() {
    AppModel app = appDao.getById("TEST_APP_ID");
    app.setStatus("正常");

    Boolean result = appDao.update(app);
    Assert.assertTrue("更新操作应返回成功", result);

    app = appDao.getById(app.getId());
    Assert.assertEquals("更新后重新获取，值应当改变", "正常", app.getStatus());
  }

  @Test
  public void testDelete() {
    Boolean result = appDao.delete("NOT_EXIST_ID");
    Assert.assertFalse("删除不存在的数据时应返回false", result);

    result = appDao.delete("TEST_APP_ID");
    Assert.assertTrue("删除存在的数据时应返回true", result);

    AppModel app = appDao.getById("TEST_APP_ID");
    Assert.assertNull("删除后数据不应存在", app);
  }

  @Test
  @Transactional
  public void testGetAll() {
    List<AppModel> apps = appDao.getAll();
    Assert.assertNotNull("获取所有列表的时候不能返回null", apps);
    Assert.assertTrue("应返回数据", apps.size() > 0);

    // 删除所有数据
    apps.forEach(app -> {
      appDao.delete(app.getId());
    });
    apps = appDao.getAll();
    Assert.assertNotNull("没有数据时也应该返回一个非null的list", apps);
    Assert.assertTrue("没有数据时返回的list对象应该为空", apps.size() == 0);
  }
}