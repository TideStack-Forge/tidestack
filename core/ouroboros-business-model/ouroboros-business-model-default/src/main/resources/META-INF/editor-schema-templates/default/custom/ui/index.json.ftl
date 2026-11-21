<#noparse>
{
  "title": "UI配置",
  "icon": "fas fa-eye",
  "tab": [
    {
      "type": "switch",
      "name": "ui.isEnabled",
      "label": "启用",
      "disabledOn": "${ !name || !title }"
    },
    </#noparse><#include "detail">
  ]
}