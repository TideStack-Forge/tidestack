#!/bin/sh

#########################################################################
# 应用各级目录
# /app
# │
# │   # 基座级目录
# ├── base
# │   ├── service
# │   │   ├── ouroboros-lib
# │   │   └── third-party-lib
# │   └── web
# │
# │   # 部署级目录
# ├── deploy
# │   ├── metadata
# │   ├── logs
# │   ├── service
# │   ├── web
# │   ├── config
# │   ├── instance.yml
# │   ├── application.yml
# │   └── ...
# │
# │ # 应用级子目录
# ├── metadata
# ├── extend
# ├── config
# ├── application.yml
# └── logback.xml
#########################################################################

# 应用根目录
APP_ROOT=/app
# 基座级目录
APP_BASE_DIR=${APP_ROOT}/base
# 部署级目录
APP_DEPLOY_DIR=${APP_ROOT}/deploy

# 基座级子目录
APP_BASE_SERVICE_DIR=${APP_BASE_DIR}/service
APP_BASE_OUROBOROS_LIB_DIR=${APP_BASE_SERVICE_DIR}/ouroboros-lib
APP_BASE_THIRD_PARTY_LIB_DIR=${APP_BASE_SERVICE_DIR}/third-party-lib

# 应用级元数据目录
APP_METADATA_DIR=${APP_ROOT}/metadata

# 日志目录
APP_LOGS_PATH=${APP_DEPLOY_DIR}/logs
APP_SERVICE_LOGS_PATH=${APP_LOGS_PATH}/service
APP_WEB_LOGS_PATH=${APP_LOGS_PATH}/web

# 启动 Jar 路径
JAR_PATH=${APP_BASE_SERVICE_DIR}/ouroboros-mothership.jar


#########################################################################
# 扩展包路径
#
# 优先级：部署级自定义 > 应用级自定义 > 应用级 > 基座级
# 注：当内建元数据存在时，默认禁用部署级扩展包，除非 ENABLE_DEPLOY_EXTEND 为 true
#########################################################################
# 应用级扩展目录
APP_EXTEND_DIR=${APP_METADATA_DIR}/extend
# 应用级定制扩展目录
APP_CUSTOM_EXTEND_DIR=${APP_ROOT}/extend
# 部署级定制扩展目录
DEPLOY_CUSTOM_EXTEND_DIR=${APP_DEPLOY_DIR}/extend

# 部署级扩展包路径
DEPLOY_CLASS_PATHS=""
if [ "${ENABLE_DEPLOY_EXTEND:-true}" = true ] && [ -d ${DEPLOY_CUSTOM_EXTEND_DIR} ]; then
  DEPLOY_CLASS_PATHS="file:${DEPLOY_CUSTOM_EXTEND_DIR}"
fi

# 应用级扩展包路径
APP_CLASS_PATHS=""
if [ -d ${APP_CUSTOM_EXTEND_DIR} ]; then
  APP_CLASS_PATHS="file:${APP_CUSTOM_EXTEND_DIR}"
fi
if [ -d ${APP_EXTEND_DIR} ]; then
  APP_CLASS_PATHS="${APP_CLASS_PATHS}${APP_CLASS_PATHS:+,}file:${APP_EXTEND_DIR}"
fi

# 基座级扩展包路径
BASE_CLASS_PATHS=""
if [ -d ${APP_BASE_OUROBOROS_LIB_DIR} ]; then
  BASE_CLASS_PATHS="file:${APP_BASE_OUROBOROS_LIB_DIR}"
fi
if [ -d ${APP_BASE_THIRD_PARTY_LIB_DIR} ]; then
  BASE_CLASS_PATHS="${BASE_CLASS_PATHS}${BASE_CLASS_PATHS:+,}file:${APP_BASE_THIRD_PARTY_LIB_DIR}"
fi

build_class_paths() {
  class_path_value=$1
  class_paths=""
  if [ -n "${class_path_value}" ]; then
    old_ifs=$IFS
    IFS=';'
    for path in ${class_path_value}; do
      if [ -d "${path}" ]; then
        class_paths="${class_paths}${class_paths:+,}file:${path}"
      fi
    done
    IFS=$old_ifs
  fi
  printf '%s' "${class_paths}"
}

# 拼入自定义的 CUSTOM_CLASS_PATH
CUSTOM_CLASS_PATHS=$(build_class_paths "${CUSTOM_CLASS_PATH}")

# 拼入自定义的托底类路径 CUSTOM_BASE_CLASS_PATH
CUSTOM_BASE_CLASS_PATHS=$(build_class_paths "${CUSTOM_BASE_CLASS_PATH}")

# 合并所有路径
FINAL_CLASS_PATH="${CUSTOM_CLASS_PATHS}${CUSTOM_CLASS_PATHS:+,}${DEPLOY_CLASS_PATHS}${DEPLOY_CLASS_PATHS:+,}${APP_CLASS_PATHS}${APP_CLASS_PATHS:+,}${BASE_CLASS_PATHS}${CUSTOM_BASE_CLASS_PATHS:+,}${CUSTOM_BASE_CLASS_PATHS}"


#########################################################################
# 配置文件路径
# 优先级：实例级 > 部署级 > 应用级 > 基座级 > Jar 内部
#########################################################################
# Jar内部
INTERNAL_CONFIG_FILE=classpath:application.yml
# 基座级配置
BASE_CONFIG_FILE=${APP_BASE_SERVICE_DIR}/application-base.yml
# 应用级配置
APP_CONFIG_FILE=${APP_METADATA_DIR}/application.yml
APP_CUSTOM_CONFIG_FILE=${APP_ROOT}/application.yml
# 部署级配置
DEPLOY_CUSTOM_CONFIG_FILE=${APP_DEPLOY_DIR}/application.yml
# 实例级配置
INSTANCE_CONFIG_FILE=${APP_DEPLOY_DIR}/instance.yml

# 定义配置路径数组
CONFIG_FILES="${INTERNAL_CONFIG_FILE}"

# 基座级配置
if [ -f ${BASE_CONFIG_FILE} ]; then
  CONFIG_FILES="${CONFIG_FILES},file:${BASE_CONFIG_FILE}"
fi

# 应用级配置
if [ -f ${APP_CONFIG_FILE} ]; then
  CONFIG_FILES="${CONFIG_FILES},file:${APP_CONFIG_FILE}"
fi

# 应用级自定义配置
if [ -f ${APP_CUSTOM_CONFIG_FILE} ]; then
  CONFIG_FILES="${CONFIG_FILES},file:${APP_CUSTOM_CONFIG_FILE}"
fi
# 检查 ${APP_ROOT}/config 下的 *.yml 文件
if [ -d ${APP_ROOT}/config ] && [ "$(ls -A ${APP_ROOT}/config)" ]; then
  for file in ${APP_ROOT}/config/*.yml; do
    if [ -f ${file} ]; then
      CONFIG_FILES="${CONFIG_FILES},file:${file}"
    fi
  done
fi

# 部署级自定义配置
if [ -f ${DEPLOY_CUSTOM_CONFIG_FILE} ]; then
  CONFIG_FILES="${CONFIG_FILES},file:${DEPLOY_CUSTOM_CONFIG_FILE}"
fi
# 检查 ${APP_DEPLOY_DIR}/config 下的 *.yml 文件
if [ -d ${APP_DEPLOY_DIR}/config ] && [ "$(ls -A ${APP_DEPLOY_DIR}/config)" ]; then
  for file in ${APP_DEPLOY_DIR}/config/*.yml; do
    if [ -f ${file} ]; then
      CONFIG_FILES="${CONFIG_FILES},file:${file}"
    fi
  done
fi

# 实例级配置
if [ -f ${INSTANCE_CONFIG_FILE} ]; then
  CONFIG_FILES="${CONFIG_FILES},file:${INSTANCE_CONFIG_FILE}"
fi

#########################################################################
# 日志配置文件
#########################################################################

# 默认的 logback 配置文件
LOG_CONFIG_FILE=${APP_BASE_SERVICE_DIR}/logback.xml

# 如果应用根目录下有自定义的 logback 配置文件，则使用应用级配置
if [ -f ${APP_ROOT}/logback.xml ]; then
  LOG_CONFIG_FILE=${APP_ROOT}/logback.xml
fi

# 如果部署目录下有自定义的 logback 配置文件，则使用部署/实例级配置
if [ -f ${APP_DEPLOY_DIR}/logback.xml ]; then
  LOG_CONFIG_FILE=${APP_DEPLOY_DIR}/logback.xml
fi


#########################################################################
# 其他参数
# ENABLE_DEBUG|DEBUG 开启调试模式
# XMS、XMX 设置JVM最小最大内存
# JAVA_OPTS 设置JVM其他参数
#########################################################################
JAVA_PARAMS=""

# 调试模式
if [ "${DEBUG:-false}" = true ] || [ "${ENABLE_DEBUG:-false}" = true ]; then
  IS_DEBUG=true
  JAVA_PARAMS="${JAVA_PARAMS} -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
fi

# 设置XMS、XMX
if [ -n "${XMS}" ]; then
  JAVA_PARAMS="${JAVA_PARAMS} -Xms${XMS}"
fi
if [ -n "${XMX}" ]; then
  JAVA_PARAMS="${JAVA_PARAMS} -Xmx${XMX}"
fi

# 设置JVM其他参数
if [ -n "${JAVA_OPTS}" ]; then
  JAVA_PARAMS="${JAVA_PARAMS} ${JAVA_OPTS}"
fi


#########################################################################
# 启动服务
#########################################################################

# 日志配置文件
JAVA_PARAMS="-Dlogging.config=${LOG_CONFIG_FILE} ${JAVA_PARAMS}"
# 加载器路径
JAVA_PARAMS="-Dloader.path=${FINAL_CLASS_PATH} ${JAVA_PARAMS}"
# java.security.egd 解决随机数生成问题
JAVA_PARAMS="-Djava.security.egd=file:/dev/./urandom ${JAVA_PARAMS}"
# Jar
JAR_PARAM="-jar ${JAR_PATH}"

# Spring Boot Params
SPRING_BOOT_PARAMS="--spring.config.location=${CONFIG_FILES}"

# 如果是调试模式，或者设置了显示命令，则打印命令
if [ "${IS_DEBUG}" = true ] || [ "${SHOW_CMD:-false}" = true ] ; then
  echo "java ${JAVA_PARAMS} ${JAR_PARAM} ${SPRING_BOOT_PARAMS}"
fi

# 以 exec 方式启动服务，以确保 PID 为 1
exec java ${JAVA_PARAMS} ${JAR_PARAM} ${SPRING_BOOT_PARAMS}
