<#noparse>
{
  "title": "新建",
  "mode": "horizontal",
  "icon": "fas fa-plus",
  "body": [
    {
      "type": "switch",
      "label": "允许第三方应用委托访问",
      "name": "accessControl.add.delegable",
      "value": false,
      "description": "仅允许在客户端范围、用户本次授权和用户角色权限共同允许时访问。"
    },
    {
      "type": "switch",
      "label": "启用访问控制",
      "name": "accessControl.add.isEnabled",
      "value": false
    },
    {
      "type": "switch",
      "label": "字段访问控制",
      "name": "accessControl.add.fieldControl",
      "visibleOn": "${ accessControl.add.isEnabled }",
      "value": false
    }
  ]
}
</#noparse>