{
  "type": "wrapper",
  "className": "p-0",
  "body": [
    {
      "type": "tabs",
      "mode": "vertical",
      "tabs": [
        <#include "subViews/read">,
        <#include "subViews/create">,
        <#include "subViews/update">,
        <#include "subViews/delete">
      ]
    }
  ]
}