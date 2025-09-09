# License.jar 文件说明

这个目录用于存放 `license.jar` 文件，该文件是 tinyMediaManager 的依赖组件。

## 获取方式

### 方法1：从官方构建获取
从 tinyMediaManager 官方构建或发布版本中获取 `license.jar` 文件，并将其放置在此目录。

### 方法2：从现有安装复制
如果您已经安装了 tinyMediaManager，可以从安装目录中复制 `license.jar` 文件到此目录。

### 方法3：构建时跳过（不推荐）
如果此文件缺失，构建将使用 Maven 中央仓库中的版本，但可能导致功能限制。

## 文件要求
- 文件名：`license.jar`
- 版本：5.2.1（与 pom.xml 中的版本匹配）
- 位置：`lib/license.jar`

## 构建集成
CI/CD 流程会自动检测此文件并执行以下命令：
```bash
mvn install:install-file -Dfile=lib/license.jar -DgroupId=org.tinymediamanager -DartifactId=license -Dversion=5.2.1 -Dpackaging=jar
```

## 注意事项
- 此文件是 tinyMediaManager 的专有组件
- 确保版本与 pom.xml 中的定义一致
- 如果文件缺失，构建将使用公开版本，但功能可能受限