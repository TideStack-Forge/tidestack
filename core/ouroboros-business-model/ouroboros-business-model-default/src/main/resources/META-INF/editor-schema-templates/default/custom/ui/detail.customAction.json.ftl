<#noparse>
{
  "type": "fieldSet",
  "visibleOn": "${ ui.mode == 'custom' }",
  "title": "自定义按钮",
  "body": [
    {
      "type": "amis-schema-editor",
      "name": "ui.buttonSchema",
      "label": "自定义按钮",
      "switchLayout": false,
      "size": "sm",
      "lockRoot": true,
      "autoRemapping": true,
      "propsEditMode": true,
      "defaultSchema": {
        "type": "button",
        "label": "${title}",
        "icon": "${icon}"
      },</#noparse><#-- 因为combo的缺陷，未渲染过的子表单无法排除不可见表单项的校验，所以在这里改用函数 --><#noparse>
      "validate": "return data.ui.mode != 'custom' ? true : data.ui.buttonSchema ? true : '请配置按钮'"
    }
  ]
}
</#noparse>