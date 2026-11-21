def REGISTRY_URL = 'registry.cn-hangzhou.aliyuncs.com'
def NAMESPACE = 'ouroboros'
def DEFAULT_BASE_IMAGE = 'registry.cn-hangzhou.aliyuncs.com/ouroboros/eclipse-temurin:21-jdk-noble'
def MAVEN_DEPLOY_RELEASE_URL = 'http://maven.whxinpai.com:8202/nexus/content/repositories/releases/'
def MAVEN_DEPLOY_SNAPSHOT_URL = 'http://maven.whxinpai.com:8202/nexus/content/repositories/snapshots'

pipeline {
  agent any

  options {
    skipDefaultCheckout false
  }

  tools {
    jdk 'jdk21'
    nodejs 'NodeJS 22.23.1'
  }

  environment {
    GIT_BRANCH = "${env.GIT_BRANCH ?: params.GIT_BRANCH}"
    NPM_TOKEN = credentials('npm-publish-token')
    // 会自动生成 DOCKER_PUSH_USER_USR 和 DOCKER_PUSH_USER_PSW
    DOCKER_PUSH_USER = credentials('docker-push-user')
  }

  parameters {
    string(name: 'VERSION', defaultValue: '', description: '版本号，请按照发版规则命名，否则可能会产生意外情况')
    string(name: 'TAGS', defaultValue: '', description: '发布镜像标签（为空时根据版本号和分支自动生成，release/* 和 snapshot/* 分支不能发布正式版本）')
    string(name: 'GIT_BRANCH', defaultValue: '', description: '版本库引用，必填')
    string(name: 'WEB_IMAGE_TAG', defaultValue: '', description: 'WEB 镜像 tag（为空时取 latest）')
    string(name: 'MOTHERSHIP_IMAGE_TAG', defaultValue: '', description: '单独构建 Develop 时的基座镜像 tag（为空时取版本号）')
    booleanParam(name: 'DEPLOY_MAVEN', defaultValue: false, description: '是否部署 Maven 版本')
    booleanParam(name: 'PUBLISH_MOTHERSHIP', defaultValue: true, description: '是否发布基座')
    booleanParam(name: 'PUBLISH_DEVELOP', defaultValue: true, description: '是否发布开发平台')
    string(name: 'BASE_IMAGE', defaultValue: '', description: "基础镜像（为空时取${DEFAULT_BASE_IMAGE}）")
    string(name: 'REGISTRY_URL', defaultValue: '', description: "镜像仓库地址（为空时取${REGISTRY_URL}，若修改此参数需要同时修改 docker-push-user 凭证）")
    string(name: 'NAMESPACE', defaultValue: '', description: "镜像仓库命名空间（为空时取${NAMESPACE}），若修改此参数可能需要同时修改 docker-push-user 凭证")
    string(name: 'MAVEN_DEPLOY_RELEASE_URL', defaultValue: '', description: "MAVEN仓库正式版发布地址，为空时取${MAVEN_DEPLOY_RELEASE_URL}")
    string(name: 'MAVEN_DEPLOY_SNAPSHOT_URL', defaultValue: '', description: "MAVEN仓库SNAPSHOT版发布地址，为空时取${MAVEN_DEPLOY_SNAPSHOT_URL}")
  }

  stages {
    // 本阶段任务：
    // 1. 判断是否需要发布：
    //   1.1 手动触发：版本号参数不为空
    //   1.2 快照分支：snapshot/* 快照分支
    //   1.3 预发行分支：release/* 预发行分支
    //   1.4 版本标签：refs/tags/* 版本标签
    //   1.5 指定制品的版本标签：refs/tags/mothership/* 基座，refs/tags/develop/* 开发平台
    // 2. 从 message 中提取构建参数：
    //   2.1 Web标签：用于构建基座
    //   2.2 Base镜像：用于构建基座
    //   2.3 基座标签：用于构建开发平台
    //   2.4 镜像标签：用于发布镜像(可选)
    //   2.5 门控开关：用于手动开启/关闭 verify-* 阶段
    //   2.6 构建测试开关：用于手动开启/关闭 build 阶段测试用例
    stage('prepare') {
      steps {
        script {
          markStage('prepare')
          def ci_skip = false
          def gate_checks_enabled = true
          def build_tests_enabled = true
          def version = ''
          def message = ''
          def web_image_tag = 'latest'
          def mothership_image_tag = ''
          def base_image = DEFAULT_BASE_IMAGE
          def tags = ''
          def deploy_maven = false
          def publish_mothership = false
          def publish_develop = false
          def registry_url = params.REGISTRY_URL ?: REGISTRY_URL
          def namespace = params.NAMESPACE ?: NAMESPACE
          def mavenDeployReleaseUrl = params.MAVEN_DEPLOY_RELEASE_URL ?: MAVEN_DEPLOY_RELEASE_URL
          def mavenDeploySnapshotUrl = params.MAVEN_DEPLOY_SNAPSHOT_URL ?: MAVEN_DEPLOY_SNAPSHOT_URL

          def gitRef = env.GIT_BRANCH
          if (isFrontendWebRef(gitRef)) {
            echo "检测到前端引用 ${gitRef}，后端流水线跳过"
            ci_skip = true
          }
          else {
            message = getMessage(gitRef)
            def gateChecksOverride = getGateChecksEnabledFromMessage(message)
            def buildTestsOverride = getBuildTestsEnabledFromMessage(message)
            version = params.VERSION ?: extractVersion(gitRef) ?: ''
            if (!version) {
              error "缺少版本号"
              ci_skip = true
              gate_checks_enabled = false
              build_tests_enabled = false
            }

            // 通过检查 params.VERSION 值是否为空来判断是否是手动触发
            // 手动触发基本，参数优先，没有再检查message，最后是默认值
            else if (params.VERSION) {
              web_image_tag = params.WEB_IMAGE_TAG ?: getWebImageTagFromMessage(message) ?: 'latest'
              mothership_image_tag = params.MOTHERSHIP_IMAGE_TAG ?: getMothershipImageTagFromMessage(message) ?: version
              base_image = params.BASE_IMAGE ?: getBaseImageFromMessage(message) ?: base_image
              tags = params.TAGS ?: getTagsFromMessage(message) ?: (isInternalPublish(gitRef) ? "${version}" : generateImageTags(message))
              deploy_maven = params.DEPLOY_MAVEN
              publish_mothership = params.PUBLISH_MOTHERSHIP
              publish_develop = params.PUBLISH_DEVELOP
              gate_checks_enabled = ! (gitRef ==~ /(origin\/)?snapshot\/.+/)
              build_tests_enabled = ! (gitRef ==~ /(origin\/)?snapshot\/.+/)
              if (gateChecksOverride != null) {
                gate_checks_enabled = gateChecksOverride
              }
              if (buildTestsOverride != null) {
                build_tests_enabled = buildTestsOverride
              }
            }

            // 通过预发行分支、快照分支发布
            // 预发行分支永远不发布 Maven 版本
            // 快照分支一直发布 Maven 版本，默认跳过门控检查
            else if (gitRef ==~ /(origin\/)?(release|snapshot)\/.+/) {
              web_image_tag = getWebImageTagFromMessage(message) ?: params.WEB_IMAGE_TAG ?: web_image_tag
              mothership_image_tag = version
              base_image = getBaseImageFromMessage(message) ?: base_image
              tags = getTagsFromMessage(message) ?: generateImageTags(version) ?: version
              gate_checks_enabled = ! (gitRef ==~ /(origin\/)?snapshot\/.+/)
              build_tests_enabled = ! (gitRef ==~ /(origin\/)?snapshot\/.+/)
              if (gateChecksOverride != null) {
                gate_checks_enabled = gateChecksOverride
              }
              if (buildTestsOverride != null) {
                build_tests_enabled = buildTestsOverride
              }
              // 快照分支可以部署 Maven 版本，release 分支不允许部署
              deploy_maven = (gitRef ==~ /(origin\/)?snapshot\/.+/) && ! isSkipDeploy(message)
              if (isPublishMothershipOnly(gitRef)) {
                publish_mothership = true
                publish_develop = false
              }
              else if (isPublishDevelopOnly(gitRef)) {
                mothership_image_tag = getMothershipImageTagFromMessage(message) ?: mothership_image_tag
                publish_mothership = false
                publish_develop = true
              }
              else {
                publish_mothership = true
                publish_develop = true
              }
            }

            // 通过打标签发布
            else if (gitRef.startsWith('refs/tags/')){
              web_image_tag = getWebImageTagFromMessage(message) ?: params.WEB_IMAGE_TAG ?: web_image_tag
              mothership_image_tag = version
              base_image = getBaseImageFromMessage(message) ?: base_image
              tags = getTagsFromMessage(message) ?: generateImageTags(version)
              if (gateChecksOverride != null) {
                gate_checks_enabled = gateChecksOverride
              }
              if (buildTestsOverride != null) {
                build_tests_enabled = buildTestsOverride
              }
              if (isPublishMothershipOnly(gitRef)) {
                publish_mothership = true
                publish_develop = false
                deploy_maven = !isSkipDeploy(message)
              }
              else if (isPublishDevelopOnly(gitRef)) {
                mothership_image_tag = getMothershipImageTagFromMessage(message) ?: mothership_image_tag
                publish_mothership = false
                publish_develop = true
                deploy_maven = false
              }
              else {
                publish_mothership = true
                publish_develop = true
                deploy_maven = !isSkipDeploy(message)
              }
            }

            // 其他，跳过
            else {
              ci_skip = true
              gate_checks_enabled = false
              build_tests_enabled = false
            }
          }

          if (ci_skip) {
            echo "跳过构建"
            gate_checks_enabled = false
            build_tests_enabled = false
            publish_mothership = false
            publish_develop = false
          }
          else {
            if (isSkipPublish(message)) {
              echo "检测到跳过发布"
              publish_mothership = false
              publish_develop = false
            }
            // 为 build.sh 增加可执行
            runTrackedSh 'chmod +x build.sh'
            echo "准备构建：\n版本号=${version}\n门控检查=${gate_checks_enabled}\n构建测试=${build_tests_enabled}\n部署 Maven=${deploy_maven}\n发布基座=${publish_mothership}\n发布开发平台=${publish_develop}"
            echo "引用Web镜像标签=${web_image_tag}\n基座基础镜像=${base_image}\n开发平台基座标签=${mothership_image_tag}\n发布镜像标签=${tags}"
            echo "镜像仓库=${registry_url}\n命名空间=${namespace}"
            // 生成随机文件名
            env.STATUS_FILE = ".${version}.build-status"
          }

          // 更新环境变量
          env.CI_SKIP = ci_skip.toString()
          env.GATE_CHECKS_ENABLED = gate_checks_enabled.toString()
          env.BUILD_TESTS_ENABLED = build_tests_enabled.toString()
          env.TO_BUILD = (!ci_skip).toString()
          env.TO_DEPLOY = (!ci_skip && deploy_maven).toString()
          env.VERSION = version
          env.WEB_IMAGE_TAG = web_image_tag
          env.MOTHERSHIP_IMAGE_TAG = mothership_image_tag
          env.BASE_IMAGE = base_image
          env.TAGS = tags
          env.DEPLOY_MAVEN = deploy_maven.toString()
          env.PUBLISH_MOTHERSHIP = publish_mothership.toString()
          env.PUBLISH_DEVELOP = publish_develop.toString()
          env.REGISTRY_URL = registry_url
          env.NAMESPACE = namespace
          env.MAVEN_DEPLOY_RELEASE_URL = mavenDeployReleaseUrl
          env.MAVEN_DEPLOY_SNAPSHOT_URL = mavenDeploySnapshotUrl
          env.MAVEN_REPO_LOCAL = "${pwd()}/.m2/repository"
        }
      }
      options {
        gitlabBuilds(builds: ['prepare'])
        gitlabCommitStatus('prepare')
      }
    }

    stage('verify-node-npm-baseline') {
      when {
        expression { !env.CI_SKIP.toBoolean() && env.GATE_CHECKS_ENABLED.toBoolean() }
      }
      steps {
        script {
          runTrackedSh 'bash scripts/ci/verify-node-npm-baseline.sh'
        }
      }
      options {
        gitlabBuilds(builds: ['verify-node-npm-baseline'])
        gitlabCommitStatus('verify-node-npm-baseline')
      }
    }

    stage('prepare-maven-version') {
      when {
        expression { !env.CI_SKIP.toBoolean() }
      }
      steps {
        script {
          runTrackedSh 'mvn -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" versions:set -DnewVersion="${VERSION}" -DgenerateBackupPoms=false'
        }
      }
      options {
        gitlabBuilds(builds: ['prepare-maven-version'])
        gitlabCommitStatus('prepare-maven-version')
      }
    }

    stage('verify-checkstyle') {
      when {
        expression { !env.CI_SKIP.toBoolean() && env.GATE_CHECKS_ENABLED.toBoolean() }
      }
      steps {
        script {
          runTrackedSh 'mvn -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" checkstyle:check'
        }
      }
      options {
        gitlabBuilds(builds: ['verify-checkstyle'])
        gitlabCommitStatus('verify-checkstyle')
      }
    }

    stage('verify-pmd') {
      when {
        expression { !env.CI_SKIP.toBoolean() && env.GATE_CHECKS_ENABLED.toBoolean() }
      }
      steps {
        script {
          runTrackedSh 'mvn -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" -DskipTests -Djacoco.skip=true -Dcheckstyle.skip=true pmd:check'
        }
      }
      options {
        gitlabBuilds(builds: ['verify-pmd'])
        gitlabCommitStatus('verify-pmd')
      }
    }

    stage('verify-package-structure') {
      when {
        expression { !env.CI_SKIP.toBoolean() && env.GATE_CHECKS_ENABLED.toBoolean() }
      }
      steps {
        script {
          runTrackedSh 'bash scripts/architecture/check-package-structure.sh'
        }
      }
      options {
        gitlabBuilds(builds: ['verify-package-structure'])
        gitlabCommitStatus('verify-package-structure')
      }
    }

    stage('verify-content-storage') {
      when {
        expression { !env.CI_SKIP.toBoolean() && env.GATE_CHECKS_ENABLED.toBoolean() }
      }
      steps {
        script {
          runTrackedSh 'bash testing/integration/feature/storage/independent-content-stores/run.sh --base HEAD'
        }
      }
      options {
        gitlabBuilds(builds: ['verify-content-storage'])
        gitlabCommitStatus('verify-content-storage')
      }
    }

    // 构造 root 项目
    stage('build') {
      when {
        expression { env.TO_BUILD.toBoolean() }
      }
      steps {
        script {
          runTrackedSh "./build.sh build -v ${env.VERSION} -s ${env.STATUS_FILE} -c ${env.BUILD_TESTS_ENABLED}"
        }
      }
      options {
        gitlabBuilds(builds: ['build'])
        gitlabCommitStatus('build')
      }
    }

    // deploy root 项目
    stage('deploy') {
      when {
        expression { env.TO_DEPLOY.toBoolean() }
      }
      steps {
        script {
          runTrackedSh "./build.sh deploy -v ${env.VERSION} -s ${env.STATUS_FILE} -c ${env.BUILD_TESTS_ENABLED}"
        }
      }
      options {
        gitlabBuilds(builds: ['deploy'])
        gitlabCommitStatus('deploy')
      }
    }

    // 发布 mothership
    stage('publish-mothership') {
      when {
        expression { env.PUBLISH_MOTHERSHIP.toBoolean() }
      }
      steps {
        script {
          runTrackedSh """
          ./build.sh publish-mothership \
            -v ${env.VERSION} \
            -w ${env.WEB_IMAGE_TAG} \
            -b ${env.BASE_IMAGE} \
            -r ${env.REGISTRY_URL} \
            -n ${env.NAMESPACE} \
            -u ${env.DOCKER_PUSH_USER_USR} \
            -p ${env.DOCKER_PUSH_USER_PSW} \
            -c ${env.BUILD_TESTS_ENABLED} \
            -t ${env.TAGS} \
            -s ${env.STATUS_FILE}
            """
        }
      }
      options {
        gitlabBuilds(builds: ['publish-mothership'])
        gitlabCommitStatus('publish-mothership')
      }
    }

    // 发布 develop
    stage('publish-develop') {
      when {
        expression { env.PUBLISH_DEVELOP.toBoolean() }
      }
      steps {
        script {
          runTrackedSh """
          ./build.sh publish-develop \
            -v ${env.VERSION} \
            -r ${env.REGISTRY_URL} \
            -n ${env.NAMESPACE} \
            -u ${env.DOCKER_PUSH_USER_USR} \
            -p ${env.DOCKER_PUSH_USER_PSW} \
            -c ${env.BUILD_TESTS_ENABLED} \
            -e ${env.MOTHERSHIP_IMAGE_TAG} \
            -t ${env.TAGS} \
            -s ${env.STATUS_FILE}
            """
        }
      }
      options {
        gitlabBuilds(builds: ['publish-develop'])
        gitlabCommitStatus('publish-develop')
      }
    }
  }

  post {
    // 清理工作
    always {
      script {
        if (env.STATUS_FILE) {
          sh "rm -f ${env.STATUS_FILE}"
        }
      }
    }

    // 成功通知
    success {
      updateGitlabCommitStatus name: 'Gitlab触发', state: 'success'
      script {
        // 夜间构建不要打扰休息
        if (env.VERSION?.trim() == 'nightly') return
        def textContent = [
          '# ✅ 自动构建成功',
          '---',
          '## 发布内容',
          '',
        ]
        def deployMaven = (env.DEPLOY_MAVEN ?: 'false').toBoolean()
        def publishMothership = (env.PUBLISH_MOTHERSHIP ?: 'false').toBoolean()
        def publishDevelop = (env.PUBLISH_DEVELOP ?: 'false').toBoolean()
        def onlyPublishDevelop = !publishMothership && publishDevelop
        def publishAny = publishMothership || publishDevelop

        textContent << (deployMaven
          ? '- <font color="green">发布 Jar 包到 Maven 仓库</font>'
          : '- <font color="red">不发布 Jar 包到 Maven 仓库</font>')

        textContent << (publishMothership
          ? '- <font color="green">发布基座镜像</font>'
          : '- <font color="red">不发布基座镜像</font>')

        textContent << (publishDevelop
          ? '- <font color="green">发布开发平台镜像</font>'
          : '- <font color="red">不发布开发平台镜像</font>')

        // 发布参数
        textContent += [
          '',
          '---',
          '',
          '## 发布参数',
          '',
          "- 发布版本: ${ env.VERSION }"
        ]

        // 如果发布基座，则输出基座相关的参数
        if (publishMothership) {
          textContent += [
            "- 基础镜像: ${ env.BASE_IMAGE ?: '默认' }",
            "- 前端基座标签: ${ env.WEB_IMAGE_TAG ?: '默认' }"
          ]
        }

        // 如果只发布开发平台，则输出开发平台引用的基座
        if (onlyPublishDevelop) {
          textContent << "- 基座镜像标签: ${ env.MOTHERSHIP_IMAGE_TAG ?: '默认' }"
        }

        // 如果会发布镜像，则输出镜像的标题
        if (publishAny) {
          textContent += [
              '',
              '---',
              '',
              '## Docker镜像',
              ''
          ]
        }

        if (publishMothership) {
          textContent += [
            '### 基座镜像',
            '',
            "- **x86/amd64**: ${env.REGISTRY_URL}/${env.NAMESPACE}/ouroboros-mothership:${env.VERSION}",
            "- **arm64**: ${env.REGISTRY_URL}/${env.NAMESPACE}/ouroboros-mothership:${env.VERSION}-arm64",
            '',
            '---'
          ]
        }

        if (publishDevelop) {
          textContent += [
            '',
            "### 开发平台镜像",
            '',
            "- **x86/amd64**: ${env.REGISTRY_URL}/${env.NAMESPACE}/ouroboros-develop:${env.VERSION}",
            "- **arm64**: ${env.REGISTRY_URL}/${env.NAMESPACE}/ouroboros-develop:${env.VERSION}-arm64",
            ''
          ]
        }
        textContent += [
          '',
          '---',
          '',
          '## 更新摘要',
          '',
          buildUpdateSummary(env.GIT_BRANCH ?: params.GIT_BRANCH)
        ]
        try {
          dingtalk (
            robot: 'ouroboros-lowcode',
            type: 'MARKDOWN',
            title: '✅ 自动构建成功通知',
            text: textContent
          )
        }
        catch (Exception e) {
          echo "⚠️ [WARNING] dingtalk 方法不可用，跳过通知: ${e.message}"
        }
      }
    }

    // 失败通知
    failure {
      updateGitlabCommitStatus name: 'Gitlab触发', state: 'failed'
      script {
        // 夜间构建不要打扰休息
        if (env.VERSION?.trim() == 'nightly') return

        def buildUser = env.BUILD_USER ?: "未知用户"
        def buildNumberText = env.BUILD_URL ? "[#${env.BUILD_NUMBER}](${env.BUILD_URL})" : "#${env.BUILD_NUMBER}"
        def webImageTag = env.WEB_IMAGE_TAG ?: "未设置"
        def baseImage = env.BASE_IMAGE ?: "未设置"
        def publishMothership = (env.PUBLISH_MOTHERSHIP ?: 'false').toBoolean()
        def publishDevelop = (env.PUBLISH_DEVELOP ?: 'false').toBoolean()
        def deployMaven = (env.DEPLOY_MAVEN ?: 'false').toBoolean()
        def failedStage = env.LAST_STARTED_STAGE ?: env.STAGE_NAME ?: '未知阶段'
        def failureLog = readFailureLogSnippet()

        def textContent = [
          '# ❌ 自动构建失败',
          '---',
          '## 构建参数',
          '',
          "- **版本**: ${env.VERSION ?: '未设置'}",
          "- **是否构建基座**: ${publishMothership ? '<font color=green>是</font>' : '<font color=red>否</font>'}",
          "- **是否构建开发平台**: ${publishDevelop ? '<font color=green>是</font>' : '<font color=red>否</font>'}",
          "- **是否发布Maven**: ${deployMaven ? '<font color=green>是</font>' : '<font color=red>否</font>'}",
          "- **镜像仓库**: ${env.REGISTRY_URL ?: '未设置'}",
          "- **命名空间**: ${env.NAMESPACE ?: '未设置'}",
          "- **前端基座 tag**: ${webImageTag}",
          "- **基础运行时镜像**: ${baseImage}",
          '',
          '---',
          '',
          '## 任务信息',
          "- **触发者**: ${buildUser}",
          "- **构建编号**: ${buildNumberText}",
          '',
          '---',
          '',
          '## 失败诊断',
          '',
          "- **错误阶段**: ${failedStage}",
          '',
          '### 可捕获日志',
          '```text',
          failureLog,
          '```',
          '',
          '> 以上为最近可捕获的阶段输出，完整信息仍以 Jenkins 控制台为准。',
        ]
        try{
          dingtalk(
            robot: 'ouroboros-lowcode',
            type: 'MARKDOWN',
            title: '❌ 自动构建失败通知',
            text: textContent
          )
        }
        catch(Exception e) {
          echo "⚠️ [WARNING] dingtalk 方法不可用，跳过通知: ${e.message}"
        }
      }
    }

    aborted {
      updateGitlabCommitStatus name: 'Gitlab触发', state: 'canceled'
    }
  }
}

void markStage(String stageName) {
  env.LAST_STARTED_STAGE = stageName ?: (env.STAGE_NAME ?: '未知阶段')
}

void runTrackedSh(String command) {
  markStage(env.STAGE_NAME ?: env.LAST_STARTED_STAGE ?: '未知阶段')
  def logFile = failureLogFile()
  sh """#!/usr/bin/env bash
set -eo pipefail
rm -f ${shellQuote(logFile)}
{
${command}
} 2>&1 | tee ${shellQuote(logFile)}
"""
}

String failureLogFile() {
  return '.jenkins-last-stage.log'
}

String readFailureLogSnippet() {
  def logFile = failureLogFile()
  if (!fileExists(logFile)) {
    return '未捕获到阶段命令日志。'
  }
  def rawLog = readFile(file: logFile)
  return compactFailureLog(maskSecrets(rawLog))
}

String maskSecrets(String text) {
  def masked = text ?: ''
  [env.DOCKER_PUSH_USER_USR, env.DOCKER_PUSH_USER_PSW, env.NPM_TOKEN]
    .findAll { it }
    .each { masked = masked.replace(it.toString(), '****') }
  return masked
}

String buildUpdateSummary(String refName) {
  try {
    def changeSetSummary = formatChangeSetSummary(currentBuild.changeSets)
    if (changeSetSummary) return changeSetSummary
  }
  catch (Exception e) {
    echo "⚠️ [WARNING] 读取 Jenkins change set 失败，回退到 git 摘要: ${e.message}"
  }

  def targetRef = refName ?: 'HEAD'
  def currentCommit = safeShStdout("git rev-parse ${shellQuote(targetRef)}")
  def previousCommit = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT ?: env.GIT_PREVIOUS_COMMIT
  if (isCommitId(previousCommit) && isCommitId(currentCommit) && previousCommit != currentCommit) {
    def range = "${previousCommit}..${currentCommit}"
    def count = safeShStdout("git rev-list --count ${range}")
    def log = safeShStdout("git log --no-merges --pretty=format:%s%x09%h%x09%an -n 10 ${range}")
    def rangeSummary = formatGitRangeSummary(log, previousCommit, currentCommit, count)
    if (rangeSummary) return rangeSummary
  }

  def message = safeBuildMessage(targetRef)
  def messageSummary = formatMessageSummary(message, currentCommit)
  return messageSummary ?: '- 未获取到 SCM 更新摘要。'
}

String safeBuildMessage(String refName) {
  try {
    return getMessage(refName) ?: ''
  }
  catch (Exception e) {
    echo "⚠️ [WARNING] 读取构建提交说明失败: ${e.message}"
    return ''
  }
}

String safeShStdout(String command) {
  try {
    return sh(script: command, returnStdout: true)?.trim() ?: ''
  }
  catch (Exception e) {
    echo "⚠️ [WARNING] 执行摘要命令失败: ${command}: ${e.message}"
    return ''
  }
}

String getMessage(String refName) {
  if (refName.startsWith('refs/tags/')) {
    return gitTagMessage(refName)
  }
  return gitCommitMessage(refName)
}

String gitTagMessage(tagName) {
  def name = tagName.substring('refs/tags/'.size())
  def msg = sh(script: "git tag -n10000 -l ${shellQuote(name)}", returnStdout: true)?.trim()
  if (msg && msg.size() > name.size()) {
    return msg.substring(name.size()).trim()
  }
  return ''
}

String gitCommitId(String refName) {
  def commitId = sh(script: "git rev-parse ${shellQuote(refName)}", returnStdout: true)?.trim()
  return commitId
}

String gitCommitMessage(String refName) {
  def commitId = gitCommitId(refName)
  def message = sh(script: "git log -n1 --pretty=%B ${shellQuote(commitId)}", returnStdout: true)?.trim()
  return message
}

@NonCPS
String compactFailureLog(String text) {
  def lines = (text ?: '').readLines().findAll { it?.trim() }
  if (!lines) return '阶段命令没有输出可用日志。'

  def start = Math.max(0, lines.size() - 60)
  def snippet = lines.subList(start, lines.size()).join('\n')
    .replace('```', "'''")
    .trim()
  if (snippet.size() <= 3500) return snippet
  return snippet.substring(snippet.size() - 3500)
}

@NonCPS
String formatChangeSetSummary(def changeSets) {
  def entries = []
  changeSets?.each { changeSet ->
    changeSet.items?.each { item ->
      def msg = firstNonBlankLine(item.msg)
      if (msg) {
        def commitId = item.commitId ? item.commitId.take(8) : ''
        def author = item.author?.fullName ?: item.author?.toString() ?: ''
        entries << [msg: msg, commitId: commitId, author: author]
      }
    }
  }
  if (!entries) return ''

  def newestEntries = entries.reverse()
  def visibleEntries = newestEntries.take(10)
  def lines = ["- 变更提交: ${entries.size()} 条"]
  visibleEntries.each { entry ->
    def suffix = entry.commitId ? " (${entry.commitId}${entry.author ? ', ' + entry.author : ''})" : ''
    lines << "- ${entry.msg}${suffix}"
  }
  if (entries.size() > visibleEntries.size()) {
    lines << "- 另有 ${entries.size() - visibleEntries.size()} 条较早提交未展示"
  }
  return lines.join('\n')
}

@NonCPS
String formatGitRangeSummary(String rawLog, String previousCommit, String currentCommit, String count) {
  if (!rawLog?.trim()) return ''

  def lines = [
    "- 变更范围: ${previousCommit.take(8)}..${currentCommit.take(8)}${count ? "（${count} commits）" : ''}"
  ]
  rawLog.readLines().findAll { it?.trim() }.each { line ->
    def parts = line.split('\t')
    def msg = parts[0]?.trim()
    if (msg) {
      def commitId = parts.size() > 1 ? parts[1]?.trim() : ''
      def author = parts.size() > 2 ? parts[2]?.trim() : ''
      def suffix = commitId ? " (${commitId}${author ? ', ' + author : ''})" : ''
      lines << "- ${msg}${suffix}"
    }
  }
  return lines.join('\n')
}

@NonCPS
String formatMessageSummary(String message, String currentCommit) {
  def summaryLines = []
  if (currentCommit) {
    summaryLines << "- 当前提交: ${currentCommit.take(8)}"
  }
  (message ?: '')
    .readLines()
    .collect { it?.trim() }
    .findAll { it && !isBuildDirectiveLine(it) }
    .take(8)
    .each { summaryLines << "- ${it}" }
  return summaryLines.join('\n')
}

@NonCPS
String firstNonBlankLine(String text) {
  return (text ?: '').readLines().collect { it?.trim() }.find { it }
}

@NonCPS
Boolean isBuildDirectiveLine(String line) {
  return line ==~ /^\[\s*(web-image-tag|base-image|mothership-image-tag|tags|skip-deploy|skip-publish|gate-checks|build-tests)(\s*:|\s*\]).*$/
}

@NonCPS
Boolean isCommitId(String value) {
  return value ==~ /^[0-9a-fA-F]{7,40}$/
}

@NonCPS
String shellQuote(String value) {
  return "'${(value ?: '').replace("'", "'\"'\"'")}'"
}

@NonCPS
String generateImageTags(String version) {
  def matcher = version =~ /(\d+)\.(\d+)\.(\d+)(-.+)?(\+\d+)?/
  if (!matcher) {
    error "无效的版本号格式: ${version}"
  }
  def majorVersion = matcher[0][1]
  def minorVersion = matcher[0][2]
  def patchVersion = matcher[0][3]
  def flag = matcher[0][4] ?: ''
  def buildNumber = matcher[0][5] ?: ''
  def tags = buildNumber
    ? "${version},${majorVersion}.${minorVersion}.${patchVersion}${flag}"
    : "${version}"

  // 如果是 snapshot 或者 prerelease 版本，则直接返回 tags
  if (flag.contains('-SNAPSHOT') || flag.contains('-prerelease')) {
    return tags
  }

  // 如果是hotfix，也要发布原始版本
  if (flag.contains('-hotfix')) {
    def originalVersion = version.replaceAll('-hotfix.+$', '')
    return "${tags},${originalVersion}"
  }

  // 如果是先行版本，则带上相应的先行版本名的 latest 标签
  if (flag) {
    def flagMatcher = flag =~ /-(alpha|beta|rc)(\.\d+)?.*$/
    def flagName = flagMatcher[0][1] ?: ''
    return flagName ? "${tags},${majorVersion}.${minorVersion}.${patchVersion}-${flagName}-latest" : tags
  }

  // 如果是正式版本，则带上相应的 latest 标签
  return flag ? tags : "${tags},${majorVersion}-latest,${majorVersion}.${minorVersion}-latest,latest"
}

@NonCPS
String extractVersion(String refName) {
  def matcher = refName =~ /^.*(v|V)?(\d+\.\d+\.\d+)(-.+)?(\+\d+)?$/
  if (matcher.matches()) {
    def mainVersion = matcher[0][2]
    def flag = matcher[0][3] ?: ''
    def buildNumber = matcher[0][4] ?: ''
    // 如果是 release 分支则加上 -prerelease 后缀，忽略 buildNumber
    if (refName ==~ /(origin\/)?release\/.+/) {
      if (!flag.endsWith('-prerelease')) return mainVersion + flag + '-prerelease'
    }
    // 如果是 snapshot 分支则加上 -SNAPSHOT 后缀，忽略 buildNumber
    if (refName ==~ /(origin\/)?snapshot\/.+/) {
      // 如果 flag 以 -snapshot 结尾，去掉小写形式的 -snapshot，因为 maven 只能识别大写形式的 -SNAPSHOT
      if (flag.endsWith('-snapshot')) flag = flag.substring(0, flag.size() - 9)
      if (!flag.endsWith('-SNAPSHOT')) return mainVersion + flag + '-SNAPSHOT'
    }
    return mainVersion + flag + buildNumber
  }
  return null
}

@NonCPS
String getWebImageTagFromMessage(String message) {
  def matcher = message =~ /(?m)\[\s*web-image-tag\s*:\s*(.+?)\s*\]/
  if (matcher.find()) {
    return matcher[0][1]
  }
  return null
}

@NonCPS
String getBaseImageFromMessage(String message) {
  def matcher = message =~ /(?m)\[\s*base-image\s*:\s*(.+?)\s*\]/
  if (matcher.find()) {
    return matcher[0][1]
  }
  return null
}

@NonCPS
String getMothershipImageTagFromMessage(String message) {
  def matcher = message =~ /(?m)\[\s*mothership-image-tag\s*:\s*(.+?)\s*\]/
  if (matcher.find()) {
    return matcher[0][1]
  }
  return null
}

@NonCPS
String getTagsFromMessage(String message) {
  def matcher = message =~ /(?m)\[\s*tags\s*:\s*(.+?)\s*\]/
  if (matcher.find()) {
    // 去掉空格
    return matcher.group(1).replaceAll(/\s+/, '')
  }
  return null
}

@NonCPS
Boolean isInternalPublish(String refName) {
  // 如果是 release/* 分支、snapshot/* 分支 则是内部发布
  return refName ==~ /(origin\/)?(release|snapshot)\/.+/
}

@NonCPS
Boolean isFrontendWebRef(String refName) {
  if (!refName) return false
  return (refName ==~ /(origin\/|refs\/heads\/)?frontend-web\/.+/) ||
    (refName ==~ /refs\/tags\/frontend-web[\/-].+/)
}

@NonCPS
Boolean isPublishMothershipOnly(String refName) {
  // 如果路径中包含 mothership 则只发布基座
  return refName.contains('/mothership/')
}

@NonCPS
Boolean isPublishDevelopOnly(String refName) {
  // 如果路径中包含 develop 则只发布开发平台
  return refName.contains('/develop/')
}

@NonCPS
// 是否为正式发布
Boolean isRelease(String version) {
  // 如果是纯数字版本号，则是正式发布
  return version ==~ /^\d+\.\d+\.\d+(-.+)?(\+\d+)?$/
}

@NonCPS
// 是否为正式候选版本
Boolean isRC(String version) {
  // 如果是-rc标记的版本号，则是正式候选版本
  return version ==~ /^\d+\.\d+\.\d+(-rc\.\d+)?(\+\d+)?$/
}

@NonCPS
Boolean isSkipDeploy(String message) {
  def matcher = message =~ /(?m)\[\s*skip-deploy:\s*(true|false)\s*\]/
  if (matcher.find()) {
    return matcher.group(1) == 'true'
  }
  def matcher2 = message =~ /(?m)\[\s*(skip-deploy)\s*\]/
  if (matcher2.find()) {
    return true
  }
  return false
}

@NonCPS
Boolean isSkipPublish(String message) {
  def matcher = message =~ /(?m)\[\s*skip-publish:\s*(true|false)\s*\]/
  if (matcher.find()) {
    return matcher.group(1) == 'true'
  }
  def matcher2 = message =~ /(?m)\[\s*(skip-publish)\s*\]/
  if (matcher2.find()) {
    return true
  }
  return false
}

@NonCPS
Boolean getGateChecksEnabledFromMessage(String message) {
  def matcher = message =~ /(?m)\[\s*gate-checks\s*:\s*(on|off)\s*\]/
  if (matcher.find()) {
    return matcher.group(1) == 'on'
  }
  return null
}

@NonCPS
Boolean getBuildTestsEnabledFromMessage(String message) {
  def matcher = message =~ /(?m)\[\s*build-tests\s*:\s*(on|off)\s*\]/
  if (matcher.find()) {
    return matcher.group(1) == 'on'
  }
  return null
}
