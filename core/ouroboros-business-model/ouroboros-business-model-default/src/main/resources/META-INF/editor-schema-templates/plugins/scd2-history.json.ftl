{
  "type": "container",
  "body": [
    {
      "type": "container",
      "visibleOn": <#noparse>"${enableScd2History}"</#noparse>,
      "body": [
        {
          "type": "alert",
          "level": "info",
          "body": "SCD2历史追踪会自动生成并列历史模型，在记录变更时创建新版本并记录操作人。"
        }
      ]
    }
  ]
}
