package com.ouroboros.data.adapter

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

import java.util.HashMap
import javax.sql.DataSource

import com.alibaba.druid.pool.DruidDataSource
import com.ouroboros.data.model.DataModelMeta
import com.ouroboros.data.model.MigrationStrategy
import com.ouroboros.data.util.DataMaps
import com.ouroboros.data.station.DataStation
import com.ouroboros.data.station.SqlDataStation
import com.ouroboros.data.station.StaticDataStationProvider

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class TestSqlDataAdapterGroovy {
    static DruidDataSource dataSource = null
    static Map<String, DataStation<?>> originalStations = null

    @BeforeAll
    static void beforeAll() {
        originalStations = new HashMap<>(StaticDataStationProvider.stations)
        var dataStation = getDataStation();
        var result = dataStation.migrate()
        println(result)
    }

    @AfterAll
    static void afterAll() {
        StaticDataStationProvider.stations.clear()
        StaticDataStationProvider.stations.putAll(originalStations)
    }

    static private DataSource getDataSource() {
        if (dataSource) return dataSource
        dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:h2:mem:sandbox;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;IGNORECASE=TRUE;");
        return dataSource;
    }

    static private SqlDataAdapter getDataAdapter() {
        return new SqlDataAdapter(getDataSource());
    }

    static List<DataModelMeta> getModelMeta() {
        List<DataModelMeta> modelMetaList = new ArrayList<>();
        var userModel = DataMaps.tryToBean(DataModelMeta.class, [
                name: "users",
                primaryKeys: ["id"],
                fields: [
                        [name: "id", type: "Integer", isAutoIncrement: true],
                        [name: "name", type: "String"],
                        [name: "nickName", type: "String"],
                        [name: "parent", type: "Model", model: "user", referenceKey: "id"],
                        [name: "createdAt", type: "DateTime"]
                ]
        ]).get()

        modelMetaList.add(userModel);
        return modelMetaList;
    }

    static DataStation getDataStation() {
        var dataSource = getDataSource();
        var modelMetaList = getModelMeta();
        var dataStation = new SqlDataStation("default", dataSource, modelMetaList, MigrationStrategy.AUTO)
        StaticDataStationProvider.register("default", dataStation)
        dataStation.migrate();
        return dataStation
    }

    @Test
    void testInsert() {
        var data = [
                name     : "test",
                nickName : "test",
                parent   : 1,
                createdAt: new Date()
        ]
        var dataModel = getDataStation().getDataModel("users").get()
        var inserted = dataModel.insert(data);
        var verify = dataModel.query([name: "test"])

        assertTrue(inserted.isSuccess(), inserted.isFailure() ? inserted.getCause().toString() : "")
        assertTrue(verify.isSuccess(), verify.isFailure() ? verify.getCause().toString() : "")
    }

    @Test
    void TestQueryMap() {
        var adapter = getDataAdapter();
        var queryMap = [
                select: "id,name",
                from  : "users",
                where : [[id: ["notEqual": 1]]],
                skip  : 0,
                limit : 10
        ]
        var count = adapter.count(queryMap);
        var results = adapter.query(queryMap);

        assertTrue(count.isSuccess(), count.isFailure() ? count.getCause().toString() : "")
        assertTrue(results.isSuccess(), results.isFailure() ? results.getCause().toString() : "")

    }

    @Test
    void testDirectAdapterMutationApis() {
        var adapter = getDataAdapter()
        var dataModel = getDataStation().getDataModel("users").get()
        var name = "adapter-direct-" + System.nanoTime()
        var insertedId = adapter.insert("users", [
                name     : name,
                nickName : "before-update",
                parent   : 1,
                createdAt: new Date()
        ])

        assertTrue(insertedId.isSuccess(), insertedId.isFailure() ? insertedId.getCause().toString() : "")
        assertNotNull(insertedId.get())

        var updateResult = adapter.update("users", [id: Integer.valueOf(insertedId.get())], [nickName: "after-update"])
        assertTrue(updateResult.isSuccess(), updateResult.isFailure() ? updateResult.getCause().toString() : "")
        assertEquals(1L, updateResult.get())

        var updated = dataModel.query(["id"], [
                id      : Integer.valueOf(insertedId.get()),
                nickName: "after-update"
        ])
        assertTrue(updated.isSuccess(), updated.isFailure() ? updated.getCause().toString() : "")
        assertEquals(1, updated.get().size())

        var deleteResult = adapter.delete("users", [id: Integer.valueOf(insertedId.get())])
        assertTrue(deleteResult.isSuccess(), deleteResult.isFailure() ? deleteResult.getCause().toString() : "")
        assertEquals(1L, deleteResult.get())

        var deleted = dataModel.query(["id"], [id: Integer.valueOf(insertedId.get())])
        assertTrue(deleted.isSuccess(), deleted.isFailure() ? deleted.getCause().toString() : "")
        assertFalse(deleted.get().iterator().hasNext())
    }

    @Test
    void testDirectAdapterBatchInsertCompatibility() {
        var adapter = getDataAdapter()
        var dataModel = getDataStation().getDataModel("users").get()
        var suffix = System.nanoTime()
        var name1 = "adapter-batch-a-" + suffix
        var name2 = "adapter-batch-b-" + suffix
        var insertedIds = adapter.batchInsert("users", [
                [
                        name     : name1,
                        nickName : "batch-a",
                        parent   : 1,
                        createdAt: new Date()
                ],
                [
                        name     : name2,
                        nickName : "batch-b",
                        parent   : 1,
                        createdAt: new Date()
                ]
        ])

        assertTrue(insertedIds.isSuccess(), insertedIds.isFailure() ? insertedIds.getCause().toString() : "")
        assertEquals(2, insertedIds.get().size())

        var first = dataModel.query(["id"], [name: name1])
        var second = dataModel.query(["id"], [name: name2])
        assertTrue(first.isSuccess(), first.isFailure() ? first.getCause().toString() : "")
        assertTrue(second.isSuccess(), second.isFailure() ? second.getCause().toString() : "")
        assertEquals(1, first.get().size())
        assertEquals(1, second.get().size())
    }
}
