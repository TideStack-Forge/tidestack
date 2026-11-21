package com.ouroboros.data.station

import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

import java.lang.reflect.Field
import javax.sql.DataSource

import com.alibaba.druid.pool.DruidDataSource
import com.ouroboros.data.model.DataModelFieldMeta
import com.ouroboros.data.model.DataModelCenter
import com.ouroboros.data.model.DataModelMeta
import com.ouroboros.data.model.MigrationStrategy
import com.ouroboros.util.SPI

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

class TestDataStation {
    private static dataSource;
    private static Map<String, DataStation<?>> originalStations;
    private static Object originalDataModelMap;
    final static String userModelName = "a.b.c.User";

    private static DataSource getDataSource() {
        if (dataSource != null) return dataSource;
        dataSource = new DruidDataSource()
        dataSource.setUrl("jdbc:h2:mem:sandbox2;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MYSQL;IGNORECASE=TRUE;")
        return dataSource
    }

    static List<DataModelMeta> getModelMeta() {
        var tableMetadata = new DataModelMeta()
        var idField = new DataModelFieldMeta()
        var nameField = new DataModelFieldMeta()
        var parentField = new DataModelFieldMeta()
        var childrenField = new DataModelFieldMeta()

        tableMetadata.setName("User")
        tableMetadata.setNamespace("a.b.c")
        tableMetadata.setRawName("users")
        tableMetadata.setLabel("用户")
        tableMetadata.setMigrationStrategy(MigrationStrategy.AUTO)

        idField.setName("id")
        idField.setType("Integer")
        idField.setIsAutoIncrement(true)

        nameField.setName("name")
        nameField.setRawName("name_field")
        nameField.setType("String")
        nameField.setIsNullable(true)

        parentField.setName("parent")
        parentField.setType("Model")
        parentField.setExtraProps(["model": userModelName])
        parentField.setIsNullable(true)


        childrenField.setName("children")
        childrenField.setType("Collection")
        childrenField.setExtraProps(["model": "user", via: "parent"])

        tableMetadata.setFields([idField, nameField, parentField, childrenField])
        tableMetadata.setPrimaryKeys(["id"])
        return [tableMetadata]
    }

    static DataStation getDataStation() {
        var dataStation = new SqlDataStation("default", getDataSource(), getModelMeta(), MigrationStrategy.AUTO)
        StaticDataStationProvider.register("default", dataStation)
        DataModelCenter.refresh()
        return dataStation
    }

    @BeforeAll
    static void beforeAll() {
        originalStations = new HashMap<>(StaticDataStationProvider.stations)
        originalDataModelMap = getDataModelMapField().get(null)
        var dataStation = getDataStation()
        var result = dataStation.migrate()
        println(result)
    }

    @AfterAll
    static void afterAll() {
        StaticDataStationProvider.stations.clear()
        StaticDataStationProvider.stations.putAll(originalStations)
        getDataModelMapField().set(null, originalDataModelMap)
    }

    @Test
    void testCreateDataStation() {
        def dataStation = getDataStation()
        assertNotNull(dataStation)
    }

    @Test
    void testGetDataModel() {
        def model = getDataStation().getDataModel(userModelName)
        assertTrue(model.isPresent())
    }

    @Test
    void testDataModelQuery() {
        def model = getDataStation().getDataModel(userModelName).orElse(null)
        assertNotNull(model)
        var results = model.query([where: [name: [endsWith: 'm']]])
        assertTrue(results.isSuccess(), results.isFailure() ? results.getCause().toString() : "简单查询执行失败")
    }

    @Test
    void testDataModelCount() {
        def model = getDataStation().getDataModel(userModelName).orElse(null)
        assertNotNull(model)
        var results = model.count([name: [endsWith: 'm']])
        assertTrue(results.isSuccess(), results.isFailure() ? results.getCause().toString() : "简单查询执行失败")
    }

    @Test
    void testDataModelCreate() {
        def model = getDataStation().getDataModel(userModelName).orElse(null)
        assertNotNull(model)
        def data = [name: "test"]
        var results = model.insert(data)
        assertTrue(results.isSuccess(), results.isFailure() ? results.getCause().toString() : "简单插入执行失败")
    }

    @Test
    void testDataModelUpdate() {
        def model = getDataStation().getDataModel(userModelName).orElse(null)
        assertNotNull(model)
        def data = [name: "test1"]
        def where = [name: "test"]
        var results = model.update(where, data)
        assertTrue(results.isSuccess(), results.isFailure() ? results.getCause().toString() : "简单更新执行失败")
    }

    @Test
    void testDataModelDelete() {
        def model = getDataStation().getDataModel(userModelName).orElse(null)
        assertNotNull(model)
        def where = [name: "test1"]
        var results = model.delete(where)
        assertTrue(results.isSuccess(), results.isFailure() ? results.getCause().toString() : "简单删除执行失败")
    }

    @Test
    void testDataModelPopulate() {
        def model = getDataStation().getDataModel(userModelName).orElse(null)
        assertNotNull(model)
        def query = [
                where   : [id: ['>': 1]],
                skip    : 0,
                limit   : 100,
                populate: 'parent'
        ]
        // 在maven test时，因为 com.ouroboros.data.station.StaticDataStationFactory 没有生效，所以这里会出错
        var results = model.query(query)
        SPI.getServiceStream(DataStationProvider.class).forEach {
            println("DataStationFactory: " + it)
        }

        assertTrue(results.isSuccess(), results.isFailure() ? results.getCause().toString() : "简单填充执行失败")
    }

    @Test
    void testMigration() {
        var sql = getDataStation().migrate()
        assertTrue(sql.isRight(), sql.isLeft() ? sql.getLeft().toString() : "获取迁移SQL失败")
    }

    private static Field getDataModelMapField() {
        Field field = DataModelCenter.class.getDeclaredField("dataModelMap")
        field.setAccessible(true)
        return field
    }
}
