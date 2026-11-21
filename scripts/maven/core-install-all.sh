#!/bin/bash

packages=(
# 平台根包
ouroboros-root
# 工具包
ouroboros-util
# 公共包
ouroboros-common
# 版本控制包
ouroboros-version-control-system
# 模型管理 Web API 框架
ouroboros-model-manage-webapi-framework
# 平台配置
ouroboros-configuration
# 数据访问层
ouroboros-data
# 字段类型库
ouroboros-field-type
# Web API 模型
ouroboros-webapi-model
# UI 模型
ouroboros-ui-model
# RPC 模型
ouroboros-rpc-model
# 菜单模型
ouroboros-menu-model
# 模型管理 Web API
ouroboros-model-manage-webapi-framework
# 安全框架
ouroboros-web-security
# 组织架构
ouroboros-organization-structure
# 逻辑流
ouroboros-logicflow
# 业务模型
ouroboros-business-model
# 容器
ouroboros-container
# 应用开发管理
ouroboros-dev-app-management
# 全部能力
ouroboros-ability-all
)

# 发布失败的包
error_packages=()

# 发布成功的包
success_packages=()

# 是否运行测试用例（默认运行）
with_test=-Dmaven.test.skip=false
if [ "$1" == "skip" ]; then
  with_test=-Dmaven.test.skip=true
fi

# 依次执行发布命令
for package in ${packages[*]}; do
    echo "Installing ${package}"
    if ! (mvn clean install --file "./${package}/pom.xml" -P 'jdk8,!jdk17' $with_test); then
      error_packages[${#error_packages[*]}]=${package}
    else
      success_packages[${#success_packages[*]}]=${package}
    fi
done

# 判断是否有安装失败的包
if [ ${#error_packages[*]} -gt 0 ]; then
  echo "Install success packages: ${success_packages[*]}"
  echo "Install error packages: ${error_packages[*]}"
  exit 1
else
  echo "Install all packages success"
  exit 0
fi
