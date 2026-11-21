#!/usr/bin/env python3
"""
Maven项目包名重构脚本 (Python版)
用于重构多层级Maven项目的包名前缀，支持SpringBoot和Java SPI

作者: AI Assistant
版本: 1.0.3 (修复目录移动问题)
"""

import os
import sys
import re
import shutil
import argparse
import subprocess
from pathlib import Path
from datetime import datetime
from typing import List, Tuple, Optional
import tempfile


class Colors:
    """控制台颜色定义"""
    RED = '\033[0;31m'
    GREEN = '\033[0;32m'
    YELLOW = '\033[1;33m'
    BLUE = '\033[0;34m'
    PURPLE = '\033[0;35m'
    CYAN = '\033[0;36m'
    WHITE = '\033[1;37m'
    RESET = '\033[0m'


class Logger:
    """日志输出类"""

    @staticmethod
    def info(message: str):
        print(f"{Colors.BLUE}[INFO]{Colors.RESET} {message}")

    @staticmethod
    def success(message: str):
        print(f"{Colors.GREEN}[SUCCESS]{Colors.RESET} {message}")

    @staticmethod
    def warning(message: str):
        print(f"{Colors.YELLOW}[WARNING]{Colors.RESET} {message}")

    @staticmethod
    def error(message: str):
        print(f"{Colors.RED}[ERROR]{Colors.RESET} {message}")

    @staticmethod
    def debug(message: str):
        print(f"{Colors.PURPLE}[DEBUG]{Colors.RESET} {message}")


class PackageRefactor:
    """包名重构主类"""

    def __init__(self, old_package: str, new_package: str, project_root: str, no_backup: bool = False):
        self.old_package = old_package
        self.new_package = new_package
        self.project_root = Path(project_root).resolve()
        self.old_package_path = old_package.replace('.', '/')
        self.new_package_path = new_package.replace('.', '/')
        self.no_backup = no_backup
        self.backup_dir = None
        self.report_data = {
            'processed_files': [],
            'moved_directories': [],
            'errors': []
        }

    def validate_package_name(self, package_name: str) -> bool:
        """验证包名格式"""
        pattern = r'^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*$'
        return bool(re.match(pattern, package_name))

    def validate_parameters(self) -> bool:
        """验证参数"""
        Logger.info("验证参数...")

        if not self.validate_package_name(self.old_package):
            Logger.error(f"无效的旧包名格式: {self.old_package}")
            return False

        if not self.validate_package_name(self.new_package):
            Logger.error(f"无效的新包名格式: {self.new_package}")
            return False

        if not self.project_root.exists():
            Logger.error(f"项目目录不存在: {self.project_root}")
            return False

        if not self.project_root.is_dir():
            Logger.error(f"指定路径不是目录: {self.project_root}")
            return False

        return True

    def create_backup(self) -> bool:
        """创建项目备份"""
        if self.no_backup:
            Logger.warning("已跳过备份创建 (--no-backup 参数)")
            return True

        try:
            Logger.info("创建项目备份...")
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            self.backup_dir = self.project_root.parent / f"{self.project_root.name}_backup_{timestamp}"

            shutil.copytree(self.project_root, self.backup_dir)
            Logger.success(f"备份已创建: {self.backup_dir}")
            return True
        except Exception as e:
            Logger.error(f"创建备份失败: {e}")
            return False

    def find_files(self, patterns: List[str], exclude_dirs: Optional[List[str]] = None) -> List[Path]:
        """查找匹配模式的文件"""
        exclude_dirs = exclude_dirs or ['.git', '.svn', 'target', 'build', '.idea', '__pycache__']
        files = []

        for pattern in patterns:
            for file_path in self.project_root.rglob(pattern):
                # 排除特定目录
                if any(exclude_dir in file_path.parts for exclude_dir in exclude_dirs):
                    continue
                if file_path.is_file():
                    files.append(file_path)

        return files

    def update_pom_files(self) -> bool:
        """更新pom.xml文件中的groupId"""
        Logger.info("更新Maven pom.xml文件中的groupId...")

        pom_files = self.find_files(['pom.xml'])

        for pom_file in pom_files:
            try:
                Logger.info(f"处理: {pom_file.relative_to(self.project_root)}")

                with open(pom_file, 'r', encoding='utf-8') as f:
                    content = f.read()

                # 备份原文件
                backup_file = pom_file.with_suffix('.xml.bak')
                shutil.copy2(pom_file, backup_file)

                # 更新groupId的正则表达式
                patterns = [
                    # 项目自己的groupId
                    (rf'(<groupId>){re.escape(self.old_package)}(</groupId>)', rf'\1{self.new_package}\2'),
                    (rf'(<groupId>){re.escape(self.old_package)}\.', rf'\1{self.new_package}.'),

                    # 处理CDATA和其他特殊情况
                    (rf'({re.escape(self.old_package)})(?=\.|</groupId>)', self.new_package),
                ]

                for pattern, replacement in patterns:
                    content = re.sub(pattern, replacement, content, flags=re.MULTILINE)

                with open(pom_file, 'w', encoding='utf-8') as f:
                    f.write(content)

                self.report_data['processed_files'].append(str(pom_file.relative_to(self.project_root)))

                # 删除备份文件
                backup_file.unlink()

            except Exception as e:
                Logger.error(f"处理pom文件失败 {pom_file}: {e}")
                self.report_data['errors'].append(f"POM文件处理失败: {pom_file}: {e}")
                return False

        return True

    def update_java_files(self) -> bool:
        """更新Java源码文件"""
        Logger.info("更新Java源码文件中的包声明、import语句和代码体...")

        java_files = self.find_files(['*.java'])

        for java_file in java_files:
            try:
                Logger.info(f"处理Java文件: {java_file.relative_to(self.project_root)}")

                with open(java_file, 'r', encoding='utf-8') as f:
                    content = f.read()

                # 备份原文件
                backup_file = java_file.with_suffix('.java.bak')
                shutil.copy2(java_file, backup_file)

                # 更新package声明和import语句的正则表达式
                patterns = [
                    # package声明
                    (rf'^package {re.escape(self.old_package)};', f'package {self.new_package};'),
                    (rf'^package {re.escape(self.old_package)}\.', f'package {self.new_package}.'),

                    # import语句
                    (rf'^import {re.escape(self.old_package)}\.', f'import {self.new_package}.'),
                    (rf'^import static {re.escape(self.old_package)}\.', f'import static {self.new_package}.'),

                    # Spring注解中的包引用
                    (rf'(@ComponentScan\([^)]*")({re.escape(self.old_package)})', rf'\1{self.new_package}'),
                    (rf'(@EntityScan\([^)]*")({re.escape(self.old_package)})', rf'\1{self.new_package}'),
                    (rf'(@EnableJpaRepositories\([^)]*")({re.escape(self.old_package)})', rf'\1{self.new_package}'),
                    (rf'(@MapperScan\([^)]*")({re.escape(self.old_package)})', rf'\1{self.new_package}'),

                    # 字符串字面量中的包名（包括日志、配置等）
                    (rf'("{re.escape(self.old_package)}")', f'"{self.new_package}"'),
                    (rf'("{re.escape(self.old_package)}\.)', f'"{self.new_package}.'),
                    (rf"('{re.escape(self.old_package)}')", f"'{self.new_package}'"),
                    (rf"('{re.escape(self.old_package)}\.)", f"'{self.new_package}."),

                    # 反射中的类名引用
                    (rf'(Class\.forName\(\s*")({re.escape(self.old_package)}\.)', rf'\1{self.new_package}.'),
                    (rf'(getClass\(\)\.getClassLoader\(\)\.getResourceAsStream\(\s*")({re.escape(self.old_package_path)})', rf'\1{self.new_package_path}'),

                    # 配置文件路径
                    (rf'(classpath:\s*")({re.escape(self.old_package_path)})', rf'\1{self.new_package_path}'),
                    (rf'(/)({re.escape(self.old_package_path)})(/)', rf'\1{self.new_package_path}\3'),

                    # 注释中的包名
                    (rf'(//.*?)({re.escape(self.old_package)})(\.|$|\s)', rf'\1{self.new_package}\3'),
                    (rf'(/\*.*?\*/)({re.escape(self.old_package)})(\.|$|\s)', rf'\1{self.new_package}\3'),

                    # Javadoc中的包名
                    (rf'(@see\s+)({re.escape(self.old_package)}\.)', rf'\1{self.new_package}.'),
                    (rf'(@link\s+)({re.escape(self.old_package)}\.)', rf'\1{self.new_package}.'),

                    # 日志输出中的包名
                    (rf'(log\..*?)({re.escape(self.old_package)})(\.|")', rf'\1{self.new_package}\3'),
                    (rf'(logger\..*?)({re.escape(self.old_package)})(\.|")', rf'\1{self.new_package}\3'),

                    # 处理完整包名（以旧包名开头的完整包名）
                    (re.escape(self.old_package) + r'\.([a-zA-Z][a-zA-Z0-9_]*\.)*[a-zA-Z][a-zA-Z0-9_]*',
                    lambda m: m.group(0).replace(self.old_package, self.new_package, 1)),

                    # 处理单独的旧包名（在词边界处）
                    (r'\b' + re.escape(self.old_package) + r'\b(?!\w)', self.new_package),

                    # 处理字符串和注释中的包名（更通用的规则）
                    (r'(["\'])([^"\']*?)' + re.escape(self.old_package) + r'(\.?[^"\']*?)\1',
                    r'\1\2' + self.new_package + r'\3\1'),

                ]

                for pattern, replacement in patterns:
                    content = re.sub(pattern, replacement, content, flags=re.MULTILINE | re.DOTALL)

                with open(java_file, 'w', encoding='utf-8') as f:
                    f.write(content)

                self.report_data['processed_files'].append(str(java_file.relative_to(self.project_root)))

                # 删除备份文件
                backup_file.unlink()

            except Exception as e:
                Logger.error(f"处理Java文件失败 {java_file}: {e}")
                self.report_data['errors'].append(f"Java文件处理失败: {java_file}: {e}")
                return False

        return True

    def move_source_directories(self) -> bool:
        """移动源码目录结构"""
        Logger.info("重构源码目录结构...")

        # 查找需要移动的目录
        source_patterns = [
            f"**/src/main/groovy/{self.old_package_path}",
            f"**/src/main/java/{self.old_package_path}",
            f"**/src/test/java/{self.old_package_path}",
            f"**/src/test/groovy/{self.old_package_path}"
        ]

        moved_dirs = []

        for pattern in source_patterns:
            old_dirs = list(self.project_root.glob(pattern))

            for old_dir in old_dirs:
                if old_dir.is_dir():
                    # 计算新目录路径
                    relative_path = old_dir.relative_to(self.project_root)
                    new_relative_path = str(relative_path).replace(self.old_package_path, self.new_package_path)
                    new_dir = self.project_root / new_relative_path

                    try:
                        Logger.info(f"移动目录: {relative_path} -> {new_relative_path}")

                        # 确保新目录的父目录存在
                        new_dir.parent.mkdir(parents=True, exist_ok=True)

                        # 使用自定义的递归移动函数，智能处理各种情况
                        self._move_directory_contents(old_dir, new_dir)

                        moved_dirs.append((str(relative_path), str(new_relative_path)))

                        # 清理空的旧目录结构
                        self._cleanup_empty_directories(old_dir.parent)

                    except Exception as e:
                        Logger.error(f"移动目录失败: {old_dir} -> {new_dir}: {e}")
                        self.report_data['errors'].append(f"目录移动失败: {old_dir} -> {new_dir}: {e}")
                        return False

        self.report_data['moved_directories'] = moved_dirs
        return True

    def _move_directory_contents(self, src_dir: Path, dst_dir: Path):
        """
        递归移动目录内容，智能处理已存在的目标目录
        这个函数能够适应各种情况：目录已存在、子目录已存在、任意深度的子目录已存在等

        Args:
            src_dir: 源目录
            dst_dir: 目标目录
        """
        # 如果源目录不存在或不是目录，直接返回
        if not src_dir.exists() or not src_dir.is_dir():
            Logger.debug(f"源目录不存在或不是目录，跳过: {src_dir}")
            return

        # 如果目标目录不存在，直接移动整个目录
        if not dst_dir.exists():
            Logger.debug(f"目标目录不存在，直接移动: {src_dir} -> {dst_dir}")
            shutil.move(str(src_dir), str(dst_dir))
            return

        # 如果目标存在但不是目录，这是错误情况
        if not dst_dir.is_dir():
            raise ValueError(f"目标路径已存在但不是目录: {dst_dir}")

        # 目标目录已存在，需要递归处理内容
        Logger.debug(f"目标目录已存在，递归合并内容: {src_dir} -> {dst_dir}")

        # 遍历源目录中的所有项
        items_to_process = list(src_dir.iterdir())
        for item in items_to_process:
            src_item = item
            dst_item = dst_dir / item.name

            try:
                if src_item.is_dir():
                    # 如果是目录，递归调用
                    Logger.debug(f"  处理子目录: {src_item.name}")
                    self._move_directory_contents(src_item, dst_item)
                else:
                    # 如果是文件
                    if dst_item.exists():
                        # 目标文件已存在，先删除再移动（相当于覆盖）
                        Logger.debug(f"  覆盖已存在的文件: {dst_item.name}")
                        if dst_item.is_file():
                            dst_item.unlink()
                        elif dst_item.is_dir():
                            shutil.rmtree(dst_item)

                    # 移动文件
                    Logger.debug(f"  移动文件: {src_item.name}")
                    shutil.move(str(src_item), str(dst_item))

            except Exception as e:
                Logger.warning(f"  移动项目时出现警告 {src_item} -> {dst_item}: {e}")
                # 继续处理其他项目，不中断整个流程
                continue

        # 删除源目录（如果已经是空的）
        try:
            if src_dir.exists() and src_dir.is_dir():
                # 检查是否为空
                remaining_items = list(src_dir.iterdir())
                if not remaining_items:
                    Logger.debug(f"  删除空的源目录: {src_dir}")
                    src_dir.rmdir()
                else:
                    Logger.debug(f"  源目录仍有内容，跳过删除: {src_dir} (剩余 {len(remaining_items)} 项)")
        except Exception as e:
            Logger.debug(f"  删除源目录时出现问题 {src_dir}: {e}")
            # 不影响主流程，继续

    def _cleanup_empty_directories(self, directory: Path):
        """清理空目录"""
        try:
            while directory != self.project_root and directory.is_dir():
                if any(directory.iterdir()):
                    break
                Logger.debug(f"删除空目录: {directory.relative_to(self.project_root)}")
                directory.rmdir()
                directory = directory.parent
        except Exception:
            pass  # 忽略清理错误

    def update_spring_config_files(self) -> bool:
        """更新Spring Boot配置文件"""
        Logger.info("更新Spring Boot配置文件...")

        config_patterns = [
            'application*.properties',
            'application*.yml',
            'application*.yaml',
            'bootstrap*.properties',
            'bootstrap*.yml',
            'bootstrap*.yaml'
        ]

        config_files = self.find_files(config_patterns)

        for config_file in config_files:
            try:
                Logger.info(f"处理配置文件: {config_file.relative_to(self.project_root)}")

                with open(config_file, 'r', encoding='utf-8') as f:
                    content = f.read()

                # 备份原文件
                backup_file = config_file.with_suffix(config_file.suffix + '.bak')
                shutil.copy2(config_file, backup_file)

                # 更新包名引用
                patterns = [
                    (re.escape(self.old_package) + r'\.', self.new_package + '.'),
                    (re.escape(self.old_package), self.new_package),
                ]

                for pattern, replacement in patterns:
                    content = re.sub(pattern, replacement, content)

                with open(config_file, 'w', encoding='utf-8') as f:
                    f.write(content)

                self.report_data['processed_files'].append(str(config_file.relative_to(self.project_root)))

                # 删除备份文件
                backup_file.unlink()

            except Exception as e:
                Logger.error(f"处理配置文件失败 {config_file}: {e}")
                self.report_data['errors'].append(f"配置文件处理失败: {config_file}: {e}")

        return True

    def update_spi_files(self) -> bool:
        """更新Java SPI配置文件"""
        Logger.info("更新Java SPI配置文件...")

        # 查找META-INF/services目录
        services_dirs = list(self.project_root.glob("**/META-INF/services"))

        for services_dir in services_dirs:
            if services_dir.is_dir():
                Logger.info(f"处理SPI目录: {services_dir.relative_to(self.project_root)}")

                # 重命名服务接口文件名
                for old_service_file in services_dir.iterdir():
                    if old_service_file.is_file() and self.old_package in old_service_file.name:
                        new_name = old_service_file.name.replace(self.old_package, self.new_package)
                        new_service_file = services_dir / new_name

                        Logger.info(f"重命名SPI文件: {old_service_file.name} -> {new_name}")
                        old_service_file.rename(new_service_file)

                # 更新所有SPI文件的内容
                for service_file in services_dir.iterdir():
                    if service_file.is_file():
                        try:
                            with open(service_file, 'r', encoding='utf-8') as f:
                                content = f.read()

                            # 备份原文件
                            backup_file = service_file.with_suffix(service_file.suffix + '.bak')
                            shutil.copy2(service_file, backup_file)

                            # 更新包名
                            content = content.replace(self.old_package + '.', self.new_package + '.')
                            content = content.replace(self.old_package, self.new_package)

                            with open(service_file, 'w', encoding='utf-8') as f:
                                f.write(content)

                            self.report_data['processed_files'].append(str(service_file.relative_to(self.project_root)))

                            # 删除备份文件
                            backup_file.unlink()

                        except Exception as e:
                            Logger.error(f"处理SPI文件失败 {service_file}: {e}")
                            self.report_data['errors'].append(f"SPI文件处理失败: {service_file}: {e}")

        return True

    def update_groovy_files(self) -> bool:
        """更新Groovy文件（包括Gradle构建脚本、Jenkins管道等）"""
        Logger.info("更新Groovy文件和构建脚本...")

        # Groovy文件模式
        groovy_patterns = [
            '*.groovy',
            '*.gradle',
            'build.gradle*',
            'settings.gradle*',
            'Jenkinsfile*',
            'pipeline.groovy',
            '*.gvy'
        ]

        groovy_files = self.find_files(groovy_patterns)

        for groovy_file in groovy_files:
            try:
                with open(groovy_file, 'r', encoding='utf-8') as f:
                    content = f.read()

                if self.old_package in content:
                    Logger.info(f"处理Groovy文件: {groovy_file.relative_to(self.project_root)}")

                    # 备份原文件
                    backup_file = groovy_file.with_suffix(groovy_file.suffix + '.bak')
                    shutil.copy2(groovy_file, backup_file)

                    # 更新包名引用的正则表达式
                    patterns = [
                        # Gradle依赖声明
                        (rf"(group\s*[:=]\s*['\"]){re.escape(self.old_package)}(['\"])", rf"\1{self.new_package}\2"),
                        (rf"(['\"]){re.escape(self.old_package)}([:.])", rf"\1{self.new_package}\2"),

                        # 包名字符串
                        (rf"(['\"]){re.escape(self.old_package)}(['\"])", rf"\1{self.new_package}\2"),
                        (rf"(['\"]){re.escape(self.old_package)}\.", rf"\1{self.new_package}."),

                        # Jenkins管道中的包名引用
                        (rf"(package\s*[:=]\s*['\"]){re.escape(self.old_package)}", rf"\1{self.new_package}"),

                        # 其他可能的包名引用
                        (re.escape(self.old_package) + r'\.', self.new_package + '.'),
                    ]

                    for pattern, replacement in patterns:
                        content = re.sub(pattern, replacement, content, flags=re.MULTILINE)

                    with open(groovy_file, 'w', encoding='utf-8') as f:
                        f.write(content)

                    self.report_data['processed_files'].append(str(groovy_file.relative_to(self.project_root)))

                    # 删除备份文件
                    backup_file.unlink()

            except Exception as e:
                Logger.warning(f"处理Groovy文件时出现问题 {groovy_file}: {e}")

        return True

    def update_shell_scripts(self) -> bool:
        """更新Shell脚本文件（包括build.sh等构建脚本）"""
        Logger.info("更新Shell脚本文件...")

        # Shell脚本模式
        shell_patterns = [
            '*.sh',
            '*.bash',
            'build.*',
            'deploy.*',
            'start.*',
            'stop.*',
            'run.*',
            '*.bat',  # Windows批处理文件
            '*.cmd',
            '*.ps1'  # PowerShell脚本
        ]

        shell_files = self.find_files(shell_patterns)

        for shell_file in shell_files:
            try:
                # 尝试不同的编码
                content = None
                for encoding in ['utf-8', 'gbk', 'latin-1']:
                    try:
                        with open(shell_file, 'r', encoding=encoding) as f:
                            content = f.read()
                        break
                    except UnicodeDecodeError:
                        continue

                if content is None:
                    Logger.warning(f"无法读取脚本文件 {shell_file}，跳过处理")
                    continue

                if self.old_package in content:
                    Logger.info(f"处理Shell脚本文件: {shell_file.relative_to(self.project_root)}")

                    # 备份原文件
                    backup_file = shell_file.with_suffix(shell_file.suffix + '.bak')
                    shutil.copy2(shell_file, backup_file)

                    # 更新包名引用的正则表达式
                    patterns = [
                        # Maven命令中的包名
                        (rf"(-Dpackage\s*=\s*){re.escape(self.old_package)}", rf"\1{self.new_package}"),
                        (rf"(-DgroupId\s*=\s*){re.escape(self.old_package)}", rf"\1{self.new_package}"),

                        # Java命令中的包名
                        (rf"(java\s+[^\s]*\s+){re.escape(self.old_package)}\.", rf"\1{self.new_package}."),
                        (rf"(-cp\s+[^:;]*){re.escape(self.old_package_path)}", rf"\1{self.new_package_path}"),

                        # 路径中的包名
                        (re.escape(self.old_package_path), self.new_package_path),

                        # 字符串中的包名
                        (rf'(["\']){re.escape(self.old_package)}(["\'])', rf'\1{self.new_package}\2'),
                        (rf'(["\']){re.escape(self.old_package)}\.', rf'\1{self.new_package}.'),

                        # 变量赋值中的包名
                        (rf"(PACKAGE\s*=\s*[\"']?){re.escape(self.old_package)}", rf"\1{self.new_package}"),
                        (rf"(GROUP_ID\s*=\s*[\"']?){re.escape(self.old_package)}", rf"\1{self.new_package}"),
                        (rf"(BASE_PACKAGE\s*=\s*[\"']?){re.escape(self.old_package)}", rf"\1{self.new_package}"),

                        # 环境变量中的包名
                        (r"(\$\{[^}]*PACKAGE[^}]*\}\s*[=/]\s*[\"']?)" + re.escape(self.old_package), r"\1" + self.new_package),

                        # 其他常见的包名引用
                        (re.escape(self.old_package) + r'(?=\.|$|\s|["\'])', self.new_package),
                    ]

                    for pattern, replacement in patterns:
                        content = re.sub(pattern, replacement, content, flags=re.MULTILINE | re.IGNORECASE)

                    # 使用原始编码写回文件
                    for encoding in ['utf-8', 'gbk', 'latin-1']:
                        try:
                            with open(shell_file, 'w', encoding=encoding) as f:
                                f.write(content)
                            break
                        except UnicodeEncodeError:
                            continue

                    self.report_data['processed_files'].append(str(shell_file.relative_to(self.project_root)))

                    # 删除备份文件
                    backup_file.unlink()

            except Exception as e:
                Logger.warning(f"处理Shell脚本文件时出现问题 {shell_file}: {e}")

        return True

    def update_spring_factories_files(self) -> bool:
        """更新Spring Boot的spring.factories配置文件"""
        Logger.info("更新Spring Boot的spring.factories配置文件...")

        # 查找META-INF/spring.factories文件
        factories_files = self.find_files(['spring.factories'])

        for factories_file in factories_files:
            try:
                Logger.info(f"处理spring.factories文件: {factories_file.relative_to(self.project_root)}")

                with open(factories_file, 'r', encoding='utf-8') as f:
                    content = f.read()

                if self.old_package in content:
                    # 备份原文件
                    backup_file = factories_file.with_suffix('.factories.bak')
                    shutil.copy2(factories_file, backup_file)

                    # 更新spring.factories中的类名引用
                    patterns = [
                        # 配置键中的包名（如org.springframework.boot.autoconfigure.EnableAutoConfiguration）
                        (re.escape(self.old_package) + r'\.([a-zA-Z][a-zA-Z0-9_]*\.)*[a-zA-Z][a-zA-Z0-9_]*=',
                        lambda m: m.group(0).replace(self.old_package, self.new_package, 1)),

                        # 配置值中的包名（多个类名用逗号分隔）
                        (r'=([^\n]*?)' + re.escape(self.old_package) + r'\.([a-zA-Z][a-zA-Z0-9_]*\.)*[a-zA-Z][a-zA-Z0-9_]*',
                        lambda m: m.group(0).replace(self.old_package, self.new_package)),

                        # 续行中的包名（以反斜杠\结尾的行）
                        (r'\\[\s]*\n[\s]*' + re.escape(self.old_package) + r'\.([a-zA-Z][a-zA-Z0-9_]*\.)*[a-zA-Z][a-zA-Z0-9_]*',
                        lambda m: m.group(0).replace(self.old_package, self.new_package)),
                    ]

                    for pattern, replacement in patterns:
                        if callable(replacement):
                            content = re.sub(pattern, replacement, content, flags=re.MULTILINE)
                        else:
                            content = re.sub(pattern, replacement, content, flags=re.MULTILINE)

                    # 最后进行通用替换，确保不遗漏
                    content = content.replace(self.old_package + '.', self.new_package + '.')

                    with open(factories_file, 'w', encoding='utf-8') as f:
                        f.write(content)

                    self.report_data['processed_files'].append(str(factories_file.relative_to(self.project_root)))

                    # 删除备份文件
                    backup_file.unlink()

                else:
                    Logger.debug(f"spring.factories文件中未发现旧包名: {factories_file.relative_to(self.project_root)}")

            except Exception as e:
                Logger.error(f"处理spring.factories文件失败 {factories_file}: {e}")
                self.report_data['errors'].append(f"spring.factories文件处理失败: {factories_file}: {e}")

        return True

    def update_spring_autoconfig_imports_files(self) -> bool:
        """更新Spring Boot的AutoConfiguration.imports配置文件"""
        Logger.info("更新Spring Boot的AutoConfiguration.imports配置文件...")

        # 查找META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports文件
        import_patterns = [
            'org.springframework.boot.autoconfigure.AutoConfiguration.imports',
            '**/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports'
        ]

        imports_files = self.find_files(import_patterns)

        for imports_file in imports_files:
            try:
                Logger.info(f"处理AutoConfiguration.imports文件: {imports_file.relative_to(self.project_root)}")

                with open(imports_file, 'r', encoding='utf-8') as f:
                    content = f.read()

                if self.old_package in content:
                    # 备份原文件
                    backup_file = imports_file.with_suffix('.imports.bak')
                    shutil.copy2(imports_file, backup_file)

                    # 处理AutoConfiguration.imports文件中的类名
                    # 这个文件格式很简单：每行一个完全限定的类名
                    lines = content.split('\n')
                    updated_lines = []

                    for line in lines:
                        stripped_line = line.strip()
                        # 跳过空行和注释行
                        if not stripped_line or stripped_line.startswith('#'):
                            updated_lines.append(line)
                            continue

                        # 替换类名中的包名
                        if self.old_package in stripped_line:
                            # 确保只替换包名部分，不影响类名
                            if stripped_line.startswith(self.old_package + '.'):
                                updated_line = stripped_line.replace(self.old_package + '.', self.new_package + '.', 1)
                                updated_lines.append(line.replace(stripped_line, updated_line))
                                Logger.debug(f"替换类名: {stripped_line} -> {updated_line}")
                            elif stripped_line == self.old_package:
                                # 处理恰好等于旧包名的情况（虽然不太可能）
                                updated_lines.append(line.replace(stripped_line, self.new_package))
                            else:
                                updated_lines.append(line)
                        else:
                            updated_lines.append(line)

                    # 写回文件
                    updated_content = '\n'.join(updated_lines)
                    with open(imports_file, 'w', encoding='utf-8') as f:
                        f.write(updated_content)

                    self.report_data['processed_files'].append(str(imports_file.relative_to(self.project_root)))

                    # 删除备份文件
                    backup_file.unlink()

                else:
                    Logger.debug(f"AutoConfiguration.imports文件中未发现旧包名: {imports_file.relative_to(self.project_root)}")

            except Exception as e:
                Logger.error(f"处理AutoConfiguration.imports文件失败 {imports_file}: {e}")
                self.report_data['errors'].append(f"AutoConfiguration.imports文件处理失败: {imports_file}: {e}")

        return True

    def update_other_files(self) -> bool:
        """更新其他可能包含包名的文件"""
        Logger.info("更新其他配置文件...")

        # XML配置文件
        xml_files = self.find_files(['*.xml'], exclude_dirs=['.git', '.svn', 'target', 'build', '.idea'])

        for xml_file in xml_files:
            if xml_file.name == 'pom.xml':
                continue  # pom.xml已经处理过了

            try:
                with open(xml_file, 'r', encoding='utf-8') as f:
                    content = f.read()

                if self.old_package in content:
                    Logger.info(f"处理XML配置文件: {xml_file.relative_to(self.project_root)}")

                    # 备份原文件
                    backup_file = xml_file.with_suffix('.xml.bak')
                    shutil.copy2(xml_file, backup_file)

                    content = content.replace(self.old_package + '.', self.new_package + '.')
                    content = content.replace(self.old_package, self.new_package)

                    with open(xml_file, 'w', encoding='utf-8') as f:
                        f.write(content)

                    self.report_data['processed_files'].append(str(xml_file.relative_to(self.project_root)))

                    # 删除备份文件
                    backup_file.unlink()

            except Exception as e:
                Logger.warning(f"处理XML文件时出现问题 {xml_file}: {e}")

        # JSON配置文件
        json_patterns = ['*.json', 'package.json', 'tsconfig.json']
        json_files = self.find_files(json_patterns)

        for json_file in json_files:
            try:
                with open(json_file, 'r', encoding='utf-8') as f:
                    content = f.read()

                if self.old_package in content:
                    Logger.info(f"处理JSON配置文件: {json_file.relative_to(self.project_root)}")

                    # 备份原文件
                    backup_file = json_file.with_suffix('.json.bak')
                    shutil.copy2(json_file, backup_file)

                    # JSON中的包名更新需要更小心，避免破坏JSON结构
                    patterns = [
                        (r'("' + re.escape(self.old_package) + r'")', f'"{self.new_package}"'),
                        (r'("' + re.escape(self.old_package) + r'\\\.)', f'"{self.new_package}.'),
                    ]

                    for pattern, replacement in patterns:
                        content = re.sub(pattern, replacement, content)

                    with open(json_file, 'w', encoding='utf-8') as f:
                        f.write(content)

                    self.report_data['processed_files'].append(str(json_file.relative_to(self.project_root)))

                    # 删除备份文件
                    backup_file.unlink()

            except Exception as e:
                Logger.warning(f"处理JSON文件时出现问题 {json_file}: {e}")

        # Docker相关文件
        docker_patterns = ['Dockerfile*', 'docker-compose*.yml', 'docker-compose*.yaml', '*.dockerfile']
        docker_files = self.find_files(docker_patterns)

        for docker_file in docker_files:
            try:
                with open(docker_file, 'r', encoding='utf-8') as f:
                    content = f.read()

                if self.old_package in content:
                    Logger.info(f"处理Docker文件: {docker_file.relative_to(self.project_root)}")

                    # 备份原文件
                    backup_file = docker_file.with_suffix(docker_file.suffix + '.bak')
                    shutil.copy2(docker_file, backup_file)

                    # Docker文件中的包名更新
                    patterns = [
                        (re.escape(self.old_package_path), self.new_package_path),
                        (re.escape(self.old_package) + r'\.', self.new_package + '.'),
                        (re.escape(self.old_package), self.new_package),
                    ]

                    for pattern, replacement in patterns:
                        content = re.sub(pattern, replacement, content)

                    with open(docker_file, 'w', encoding='utf-8') as f:
                        f.write(content)

                    self.report_data['processed_files'].append(str(docker_file.relative_to(self.project_root)))

                    # 删除备份文件
                    backup_file.unlink()

            except Exception as e:
                Logger.warning(f"处理Docker文件时出现问题 {docker_file}: {e}")

        # 文档文件
        doc_patterns = ['README*', '*.md', '*.txt', '*.rst']
        doc_files = self.find_files(doc_patterns)

        for doc_file in doc_files:
            try:
                with open(doc_file, 'r', encoding='utf-8') as f:
                    content = f.read()

                if self.old_package in content:
                    Logger.info(f"处理文档文件: {doc_file.relative_to(self.project_root)}")

                    # 备份原文件
                    backup_file = doc_file.with_suffix(doc_file.suffix + '.bak')
                    shutil.copy2(doc_file, backup_file)

                    content = content.replace(self.old_package, self.new_package)

                    with open(doc_file, 'w', encoding='utf-8') as f:
                        f.write(content)

                    self.report_data['processed_files'].append(str(doc_file.relative_to(self.project_root)))

                    # 删除备份文件
                    backup_file.unlink()

            except Exception as e:
                Logger.warning(f"处理文档文件时出现问题 {doc_file}: {e}")

        return True

    def validate_maven_project(self) -> bool:
        """验证Maven项目"""
        Logger.info("验证Maven项目结构...")

        try:
            # 检查是否有maven命令
            result = subprocess.run(['mvn', '--version'],
                                  capture_output=True,
                                  text=True,
                                  cwd=self.project_root)

            if result.returncode != 0:
                Logger.warning("未找到Maven命令，跳过自动验证")
                return True

            # 运行Maven验证
            Logger.info("运行Maven验证...")
            result = subprocess.run(['mvn', 'validate', '-q'],
                                  capture_output=True,
                                  text=True,
                                  cwd=self.project_root)

            if result.returncode == 0:
                Logger.success("Maven项目验证通过")
                return True
            else:
                Logger.warning(f"Maven项目验证失败: {result.stderr}")
                return False

        except FileNotFoundError:
            Logger.warning("未找到Maven命令，跳过自动验证")
            return True
        except Exception as e:
            Logger.warning(f"Maven验证过程中出现错误: {e}")
            return True

    def generate_report(self) -> bool:
        """生成重构报告"""
        report_file = self.project_root / 'package_refactor_report.txt'
        Logger.info(f"生成重构报告: {report_file}")

        try:
            with open(report_file, 'w', encoding='utf-8') as f:
                f.write("Maven项目包名重构报告\n")
                f.write("=" * 50 + "\n\n")
                f.write(f"重构时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
                f.write(f"项目路径: {self.project_root}\n")
                f.write(f"旧包名前缀: {self.old_package}\n")
                f.write(f"新包名前缀: {self.new_package}\n")

                # 根据是否有备份显示不同的信息
                if self.backup_dir:
                    f.write(f"备份位置: {self.backup_dir}\n\n")
                else:
                    f.write("备份状态: 已跳过备份 (--no-backup参数)\n\n")

                f.write("已处理的文件类型:\n")
                f.write("- Maven pom.xml文件\n")
                f.write("- Java源码文件 (.java) - 包括代码体中的包名引用\n")
                f.write("- Spring Boot配置文件 (application.properties, application.yml)\n")
                f.write("- Spring Boot自动配置文件 (spring.factories)\n")
                f.write("- Spring Boot自动配置导入文件 (AutoConfiguration.imports)\n")
                f.write("- Java SPI配置文件 (META-INF/services/*)\n")
                f.write("- Groovy文件和构建脚本 (*.groovy, *.gradle, Jenkinsfile)\n")
                f.write("- Shell脚本文件 (*.sh, build.sh, deploy.sh等)\n")
                f.write("- Docker相关文件 (Dockerfile, docker-compose.yml)\n")
                f.write("- JSON配置文件 (*.json)\n")
                f.write("- XML配置文件 (*.xml)\n")
                f.write("- 文档文件 (README.*, *.md, *.txt)\n\n")

                if self.report_data['moved_directories']:
                    f.write("目录结构变更:\n")
                    for old_dir, new_dir in self.report_data['moved_directories']:
                        f.write(f"- {old_dir} -> {new_dir}\n")
                    f.write("\n")

                f.write(f"处理的文件总数: {len(self.report_data['processed_files'])}\n")
                if self.report_data['processed_files']:
                    f.write("处理的文件列表:\n")
                    for file_path in sorted(self.report_data['processed_files']):
                        f.write(f"- {file_path}\n")
                    f.write("\n")

                if self.report_data['errors']:
                    f.write(f"错误数量: {len(self.report_data['errors'])}\n")
                    f.write("错误列表:\n")
                    for error in self.report_data['errors']:
                        f.write(f"- {error}\n")
                    f.write("\n")

                f.write("注意事项:\n")
                f.write("1. 请检查所有修改是否正确\n")
                f.write("2. 运行测试确保功能正常\n")
                f.write("3. 检查IDE的项目设置\n")
                f.write("4. 更新版本控制系统的忽略文件\n")
                f.write("5. 通知团队成员相关变更\n")
                f.write("6. 检查日志输出和字符串常量中的包名引用\n")
                f.write("7. 验证反射调用和动态类加载是否正确\n")

                # 如果没有备份，添加额外的注意事项
                if self.no_backup:
                    f.write("8. 【重要】因为跳过了备份，建议在提交代码前仔细检查所有变更\n")

            return True

        except Exception as e:
            Logger.error(f"生成报告失败: {e}")
            return False

    def cleanup_backup_files(self):
        """清理临时备份文件"""
        Logger.info("清理临时备份文件...")

        backup_patterns = ['*.bak']
        backup_files = self.find_files(backup_patterns)

        for backup_file in backup_files:
            try:
                backup_file.unlink()
            except Exception as e:
                Logger.warning(f"删除备份文件失败 {backup_file}: {e}")

    def run_refactor(self) -> bool:
        """执行重构过程"""
        Logger.info(f"项目根目录: {self.project_root}")
        Logger.info(f"旧包名前缀: {self.old_package}")
        Logger.info(f"新包名前缀: {self.new_package}")
        Logger.info(f"旧包名路径: {self.old_package_path}")
        Logger.info(f"新包名路径: {self.new_package_path}")

        if self.no_backup:
            Logger.warning("已启用跳过备份模式 (--no-backup)")

        # 验证参数
        if not self.validate_parameters():
            return False

        # 显示将要执行的操作
        print(f"\n{Colors.YELLOW}此操作将修改项目中的以下内容:{Colors.RESET}")
        print("  - 所有pom.xml文件中的groupId")
        print("  - Java源码文件中的package声明、import语句和代码体中的包名引用")
        print("  - 源码目录结构")
        print("  - Spring Boot配置文件")
        print("  - Java SPI配置文件")
        print("  - Groovy文件和构建脚本 (*.groovy, *.gradle, Jenkinsfile)")
        print("  - Shell脚本文件 (*.sh, build.sh, deploy.sh等)")
        print("  - Docker相关文件 (Dockerfile, docker-compose.yml)")
        print("  - JSON配置文件")
        print("  - 其他相关配置文件和文档")

        if self.no_backup:
            print(f"\n{Colors.RED}⚠️  注意: 已启用 --no-backup 参数，不会创建项目备份！{Colors.RESET}")
            print("请确保你已经在版本控制系统中提交了当前状态，或者手动创建了备份。")

        print()

        # 确认操作
        response = input("确认要继续吗? (y/N): ").strip().lower()
        if response not in ['y', 'yes']:
            Logger.info("操作已取消")
            return False

        # 创建备份 (根据no_backup参数决定)
        if not self.create_backup():
            return False

        try:
            # 执行重构步骤
            steps = [
                ("更新pom.xml文件", self.update_pom_files),
                ("更新Java源码文件", self.update_java_files),
                ("移动源码目录", self.move_source_directories),
                ("更新Spring配置文件", self.update_spring_config_files),
                ("更新Java SPI文件", self.update_spi_files),
                ("更新Spring Boot的spring.factories文件", self.update_spring_factories_files),
                ("更新Spring Boot的AutoConfiguration.imports文件", self.update_spring_autoconfig_imports_files),
                ("更新Groovy文件和构建脚本", self.update_groovy_files),
                ("更新Shell脚本文件", self.update_shell_scripts),
                ("更新其他配置文件", self.update_other_files),
            ]

            for step_name, step_func in steps:
                Logger.info(f"执行步骤: {step_name}")
                if not step_func():
                    Logger.error(f"步骤失败: {step_name}")
                    return False

            # 清理备份文件
            self.cleanup_backup_files()

            # 验证Maven项目
            self.validate_maven_project()

            # 生成报告
            self.generate_report()

            Logger.success("包名重构完成！")

            if self.backup_dir:
                Logger.info(f"备份已保存至: {self.backup_dir}")
            else:
                Logger.warning("未创建备份，请确保项目状态已在版本控制系统中保存")

            Logger.info(f"重构报告已生成: {self.project_root}/package_refactor_report.txt")

            # 显示检查清单
            print(f"\n{Colors.YELLOW}重构后的检查清单:{Colors.RESET}")
            print("□ 运行 'mvn clean compile' 验证编译")
            print("□ 运行测试用例确保功能正常")
            print("□ 检查IDE项目配置")
            print("□ 更新CI/CD配置")
            print("□ 检查Docker文件或其他部署脚本")
            print("□ 更新项目文档")
            print("□ 检查日志输出中的包名是否正确")
            print("□ 验证反射调用和资源路径")
            print("□ 提交代码变更")

            return True

        except Exception as e:
            Logger.error(f"重构过程中出现错误: {e}")
            return False


def main():
    """主函数"""
    parser = argparse.ArgumentParser(
        description="Maven项目包名重构脚本 (修复目录移动问题版本)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  %(prog)s com.xinpia com.ciecc
  %(prog)s com.xinpia com.ciecc -p /path/to/project
  %(prog)s com.xinpai.ouroboros com.ouroboros --project-root /path/to/project
  %(prog)s com.xinpia com.ciecc --no-backup

说明:
  此脚本用于重构多层级Maven项目的包名前缀，支持SpringBoot 2.7和Java SPI。
  重构前默认会自动创建项目备份，重构后生成详细报告。
  新版本增强了对代码体中包名引用的处理能力。
  使用 --no-backup 参数可以跳过备份创建，适用于已经有版本控制保护的项目。

  v1.0.3 修复了目录移动时的逻辑问题，避免创建多余的嵌套目录。
        """
    )

    parser.add_argument('old_package', help='旧包名前缀 (如: com.xinpai.ouroboros)')
    parser.add_argument('new_package', help='新包名前缀 (如: com.ouroboros)')
    parser.add_argument('-p', '--project-root', default='.',
                       help='Maven项目根目录，默认为当前目录')
    parser.add_argument('--no-backup', action='store_true',
                       help='跳过备份创建，适用于已经有版本控制保护的项目')
    parser.add_argument('--version', action='version', version='%(prog)s 1.0.3')

    args = parser.parse_args()

    # 创建重构实例并执行
    refactor = PackageRefactor(args.old_package, args.new_package, args.project_root, args.no_backup)

    try:
        success = refactor.run_refactor()
        sys.exit(0 if success else 1)
    except KeyboardInterrupt:
        Logger.warning("操作被用户中断")
        sys.exit(1)
    except Exception as e:
        Logger.error(f"执行过程中出现未预期的错误: {e}")
        sys.exit(1)


if __name__ == '__main__':
    main()