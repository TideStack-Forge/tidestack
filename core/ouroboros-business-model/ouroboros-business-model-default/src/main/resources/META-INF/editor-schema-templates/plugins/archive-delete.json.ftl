{
  "type": "container",
  "body": [
    {
      "type": "container",
      "visibleOn": <#noparse>"${enableArchiveDelete}"</#noparse>,
      "body": [
        {
          "type": "alert",
          "level": "info",
          "body": "归档删除会自动生成并列归档模型，删除时将记录复制到归档模型中，然后从源表物理删除。"
        }
      ]
    }
  ]
}
