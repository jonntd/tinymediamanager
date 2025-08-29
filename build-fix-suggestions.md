# GitHub Actions 构建修复建议

## 常见问题和解决方案

### 1. Java 版本问题
如果构建失败提示 Java 版本问题：
```yaml
- name: Set up JDK 17
  uses: actions/setup-java@v4
  with:
    java-version: '17'
    distribution: 'temurin'
```

### 2. Maven 内存问题
如果出现内存不足错误：
```yaml
env:
  MAVEN_OPTS: '-Xmx2048m -Xms1024m'
```

### 3. 权限问题
如果推送或发布失败：
```yaml
permissions:
  contents: write
  packages: write
```

### 4. 依赖下载问题
如果依赖下载失败，添加重试：
```yaml
- name: Cache Maven dependencies
  uses: actions/cache@v4
  with:
    path: ~/.m2/repository
    key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
```

### 5. 原生库问题
确保所有平台的原生库都存在：
- native/windows/
- native/linux/
- native/mac/
- native/arm/

### 6. 程序集配置问题
检查 src/assembly/ 目录下的 XML 配置文件。

## 实时监控命令
```bash
# 查看构建状态
./test-github-actions.sh monitor

# 检查配置
./test-github-actions.sh diagnosis

# 修复常见问题
./test-github-actions.sh fix
```

