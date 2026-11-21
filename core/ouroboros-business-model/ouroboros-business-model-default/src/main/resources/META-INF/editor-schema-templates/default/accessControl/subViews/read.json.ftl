<#noparse>
{
  "title": "查询",
  "mode": "horizontal",
  "icon": "fas fa-magnifying-glass",
  "body": [
    {
      "type": "switch",
      "label": "允许第三方应用委托访问",
      "name": "accessControl.search.delegable",
      "value": false,
      "description": "仅允许在客户端范围、用户本次授权和用户角色权限共同允许时访问。"
    },
    {
      "type": "switch",
      "label": "启用访问控制",
      "name": "accessControl.search.isEnabled",
      "value": false
    },
    {
      "type": "switch",
      "label": "字段访问控制",
      "name": "accessControl.search.fieldControl",
      "visibleOn": "${ accessControl.search.isEnabled }",
      "value": false
    },
    {
      "type": "combo",
      "label": "访问控制方式",
      "name": "accessControl.search.filters",
      "visibleOn": "${ accessControl.search.isEnabled }",
      "items": [
        {
          "type": "switch",
          "label": false,
          "name": "$byEmployee",
          "value": false,
          "option": "按制单人"
        },
        {
          "type": "switch",
          "label": false,
          "name": "$byDepartment",
          "value": false,
          "option": "按部门"
        },
        {
          "type": "switch",
          "label": false,
          "name": "$byCompany",
          "value": false,
          "option": "按公司"
        }
      ]
    }
  ]
}
</#noparse>