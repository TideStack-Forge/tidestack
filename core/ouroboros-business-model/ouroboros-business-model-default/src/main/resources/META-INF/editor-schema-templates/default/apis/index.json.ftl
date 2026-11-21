{
  "type": "panel",
  "title": "接口配置",
  "bodyClassName": "p-0",
  "body": [
    {
      "type": "tabs",
      "tabsMode": "vertical",
      "tabs": [
        <#include "subViews/search">,
        <#include "subViews/getOne">,
        <#include "subViews/add">,
        <#include "subViews/update">,
        <#include "subViews/delete">
      ]
    }
  ]
}