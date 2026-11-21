import os
import re
from pathlib import Path

def find_aop_annotation_issues(root_dir):
    """检查AOP注解方法的内部调用问题"""

    # AOP相关注解
    aop_annotations = [
        '@Transactional', '@Cacheable', '@Async', '@Retryable',
        '@PreAuthorize', '@PostAuthorize', '@Secured'
    ]

    issues = []

    for java_file in Path(root_dir).rglob("*.java"):
        with open(java_file, 'r', encoding='utf-8') as f:
            content = f.read()

        # 分析每个类
        class_issues = analyze_class(content, aop_annotations, str(java_file))
        issues.extend(class_issues)

    return issues

def analyze_class(content, aop_annotations, file_path):
    """分析单个类中的AOP问题"""
    issues = []

    # 移除注释
    content_no_comments = remove_comments(content)

    # 找到类的边界
    classes = find_classes(content_no_comments)

    for class_info in classes:
        class_content = class_info['content']
        class_name = class_info['name']

        # 找到带AOP注解的方法
        annotated_methods = find_annotated_methods_in_class(class_content, aop_annotations)

        # 检查每个AOP方法的内部调用
        for method_info in annotated_methods:
            problematic_calls = find_problematic_calls(
                class_content,
                method_info['name'],
                class_name
            )

            if problematic_calls:
                issues.append({
                    'file': file_path,
                    'class': class_name,
                    'method': method_info['name'],
                    'annotation': method_info['annotation'],
                    'problematic_calls': problematic_calls
                })

    return issues

def remove_comments(content):
    """移除Java注释"""
    # 移除单行注释
    content = re.sub(r'//.*?$', '', content, flags=re.MULTILINE)
    # 移除多行注释
    content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)
    return content

def find_classes(content):
    """找到所有类定义"""
    classes = []

    # 匹配类声明
    class_pattern = r'(?:public\s+|private\s+|protected\s+)?(?:static\s+)?(?:final\s+)?class\s+(\w+).*?\{'

    for match in re.finditer(class_pattern, content):
        class_name = match.group(1)
        start_pos = match.start()

        # 找到类的结束位置（匹配大括号）
        brace_count = 0
        class_start = content.find('{', start_pos)
        current_pos = class_start

        while current_pos < len(content):
            char = content[current_pos]
            if char == '{':
                brace_count += 1
            elif char == '}':
                brace_count -= 1
                if brace_count == 0:
                    break
            current_pos += 1

        if brace_count == 0:
            class_content = content[class_start+1:current_pos]
            classes.append({
                'name': class_name,
                'content': class_content,
                'start': class_start,
                'end': current_pos
            })

    return classes

def find_annotated_methods_in_class(class_content, aop_annotations):
    """在类中找到带AOP注解的方法"""
    methods = []
    lines = class_content.split('\n')

    i = 0
    while i < len(lines):
        line = lines[i].strip()

        # 检查是否有AOP注解
        found_annotation = None
        for annotation in aop_annotations:
            if annotation in line:
                found_annotation = annotation
                break

        if found_annotation:
            # 向下查找方法声明（可能跨多行）
            j = i + 1
            method_declaration = ""

            while j < len(lines):
                method_line = lines[j].strip()
                method_declaration += " " + method_line

                # 检查是否是方法声明
                method_match = re.search(r'(?:public|private|protected).*?(\w+)\s*\(.*?\)\s*(?:throws.*?)?\s*\{', method_declaration)
                if method_match:
                    method_name = method_match.group(1)
                    # 排除构造函数
                    if not method_name[0].isupper():
                        methods.append({
                            'name': method_name,
                            'annotation': found_annotation,
                            'line': i
                        })
                    break

                j += 1
                # 避免无限循环
                if j - i > 10:
                    break

        i += 1

    return methods

def find_problematic_calls(class_content, method_name, class_name):
    """找到有问题的方法调用"""
    problematic_calls = []

    # 将类内容按行分割，便于定位行号
    lines = class_content.split('\n')

    for line_num, line in enumerate(lines, 1):
        line = line.strip()

        # 跳过注释行
        if line.startswith('//') or line.startswith('*') or line.startswith('/*'):
            continue

        # 查找方法调用
        # 1. this.methodName( - 有问题的调用
        this_call_pattern = rf'\bthis\.{re.escape(method_name)}\s*\('
        if re.search(this_call_pattern, line):
            problematic_calls.append({
                'line': line_num,
                'content': line,
                'type': 'this调用',
                'call': f'this.{method_name}()'
            })

        # 2. 直接调用 methodName( - 有问题的调用（但排除self.methodName）
        direct_call_pattern = rf'(?<!self\.)\b{re.escape(method_name)}\s*\('
        if re.search(direct_call_pattern, line) and f'self.{method_name}' not in line:
            # 进一步确认这不是方法声明
            if not re.search(rf'(?:public|private|protected).*{re.escape(method_name)}\s*\(', line):
                problematic_calls.append({
                    'line': line_num,
                    'content': line,
                    'type': '直接调用',
                    'call': f'{method_name}()'
                })

    return problematic_calls

def print_issues(issues):
    """打印发现的问题"""
    if not issues:
        print("✅ 没有发现AOP代理问题！")
        return

    print(f"⚠️  发现 {len(issues)} 个潜在的AOP代理问题：\n")

    for i, issue in enumerate(issues, 1):
        print(f"问题 {i}:")
        print(f"  文件: {issue['file']}")
        print(f"  类名: {issue['class']}")
        print(f"  方法: {issue['method']} (注解: {issue['annotation']})")
        print(f"  问题调用:")

        for call in issue['problematic_calls']:
            print(f"    - 第{call['line']}行: {call['type']} -> {call['call']}")
            print(f"      代码: {call['content']}")

        print(f"  💡 建议: 使用 self.{issue['method']}() 或注入自身代理")
        print("-" * 80)

# 使用示例
if __name__ == "__main__":
    print("🔍 开始检查AOP代理问题...")
    issues = find_aop_annotation_issues("./")
    print_issues(issues)

    # 生成修复建议
    if issues:
        print("\n📋 修复建议:")
        print("1. 在类中注入自身: @Autowired private YourService self;")
        print("2. 使用代理调用: self.methodName() 替代 this.methodName() 或直接调用")
        print("3. 或者将方法提取到另一个Service类中")