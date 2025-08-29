# tinyMediaManager CI/CD 工作流说明

## 概述

这个 GitHub Actions 工作流提供了完整的多平台构建、测试和发布流程，支持 Windows、macOS 和 Linux 平台的自动化构建。

## 工作流结构

### 1. 触发条件

- **推送到分支**: `devel`, `main`
- **标签推送**: `v*` (用于发布)
- **Pull Request**: 针对 `devel`, `main` 分支
- **手动触发**: 支持选择构建类型 (`test`, `release`, `dist`)

### 2. 作业流程

#### 🧪 build-and-test (基础构建和测试)
- **运行环境**: Ubuntu Latest
- **功能**:
  - 代码检出和 Java 17 环境设置
  - Maven 依赖缓存
  - 版本提取和发布条件检查
  - 单元测试执行
  - 基础 JAR 包构建
  - 构建产物上传

#### 🏗️ build-platforms (多平台构建)
- **运行环境**: 多平台矩阵
- **支持平台**:
  - Windows x64 (windows-latest)
  - Linux x64 (ubuntu-latest)
  - Linux ARM64 (ubuntu-latest)
  - macOS x64 (macos-13)
  - macOS ARM64 (macos-latest)
- **功能**:
  - 平台特定的原生库验证
  - 使用 `dist` profile 构建完整分发包
  - 平台特定的打包和压缩
  - 构建产物上传

#### 🚀 create-release (GitHub 发布)
- **触发条件**: 标签推送 + 发布构建
- **功能**:
  - 下载所有平台构建产物
  - 生成发布说明
  - 创建 GitHub Release
  - 上传所有平台的分发包

#### 📊 notify-status (状态通知)
- **功能**:
  - 汇总构建状态
  - 输出构建摘要
  - 提供构建结果概览

## 构建类型

### Test Build (测试构建)
- 触发条件: 普通推送、PR
- 执行内容: 基础构建和测试
- 产物: JAR 包

### Release Build (发布构建)
- 触发条件: 标签推送、手动选择 `release`/`dist`
- 执行内容: 完整多平台构建
- 产物: 所有平台的分发包

## 平台支持

| 平台 | 架构 | 程序集配置 | 原生库路径 | 输出格式 |
|------|------|------------|------------|----------|
| Windows | x64 | windows-x64 | native/windows | ZIP |
| Linux | x64 | linux-x64 | native/linux | tar.bz2 |
| Linux | ARM64 | linux-aarch64 | native/arm | tar.bz2 |
| macOS | x64 | macos-x64 | native/mac | tar.bz2 |
| macOS | ARM64 | macos-aarch64 | native/mac | tar.bz2 |

## 优化特性

### 🚀 性能优化
- **并行构建**: 多平台构建并行执行
- **依赖缓存**: Maven 依赖缓存减少下载时间
- **条件执行**: 只在需要时执行平台构建
- **内存优化**: 配置 Maven 内存参数

### 🔒 安全性
- **最小权限**: 只在需要时授予写权限
- **校验和**: 自动生成 SHA256 校验和
- **签名跳过**: 开发构建跳过代码签名

### 📦 产物管理
- **版本化命名**: 包含版本号的产物名称
- **保留期限**: 30天的产物保留期
- **完整性检查**: 构建后验证产物存在

## 使用方法

### 开发构建
```bash
# 推送到 devel 分支触发测试构建
git push origin devel
```

### 发布构建
```bash
# 创建并推送标签触发发布构建
git tag v5.2.0
git push origin v5.2.0
```

### 手动构建
1. 访问 GitHub Actions 页面
2. 选择 "Multi-Platform CI/CD Pipeline"
3. 点击 "Run workflow"
4. 选择构建类型和分支

## 故障排除

### 常见问题

1. **原生库缺失**
   - 检查 `native/` 目录下是否有对应平台的库文件
   - 确认程序集配置中的路径正确

2. **构建失败**
   - 查看具体作业的日志
   - 检查 Maven 配置和依赖

3. **发布失败**
   - 确认有足够的权限
   - 检查标签格式是否正确 (v*)

### 调试技巧

- 使用手动触发进行测试
- 检查构建产物是否正确生成
- 验证平台特定的配置

## 扩展和自定义

### 添加新平台
1. 在 `matrix.platform` 中添加新配置
2. 创建对应的程序集配置文件
3. 添加原生库文件
4. 测试构建流程

### 修改构建逻辑
- 编辑对应的作业步骤
- 更新 Maven 配置
- 测试更改效果

## 相关文件

### 核心配置文件
- `pom.xml`: Maven 构建配置
- `src/assembly/*.xml`: 平台程序集配置
- `native/`: 平台原生库
- `AppBundler/`: 打包脚本和资源

### CI/CD 工作流
- `.github/workflows/ci.yml`: 主要的多平台 CI/CD 流程
- `.github/workflows/docker-build.yml`: Docker 容器构建流程

### 配置和脚本
- `.github/config/build-config.yml`: 构建配置参数 (简化版)
- `.github/scripts/build-utils.sh`: 构建工具脚本
- `.github/scripts/monitor-builds.sh`: 构建状态监控脚本
- `build-local.sh`: 本地快速构建脚本

## 完整的 CI/CD 功能特性

### 🚀 多平台构建支持
- **Windows x64**: 完整的 Windows 分发包
- **Linux x64**: 标准 Linux 分发包
- **Linux ARM64**: ARM 架构支持
- **macOS x64**: Intel Mac 支持
- **macOS ARM64**: Apple Silicon 支持

### 🔄 自动化流程
- **推送触发**: 自动构建和测试
- **标签发布**: 自动创建 GitHub Release
- **夜间构建**: 定时构建最新代码
- **性能测试**: 定期性能基准测试

### 🛡️ 安全和质量
- **依赖扫描**: OWASP 依赖检查
- **代码分析**: CodeQL 安全扫描
- **Docker 安全**: Trivy 容器扫描
- **构建验证**: 多层次的构建验证

### 📦 构建产物管理
- **版本化命名**: 自动版本标记
- **校验和生成**: SHA256 完整性验证
- **多格式支持**: ZIP、tar.bz2 格式
- **产物保留**: 可配置的保留策略

### 🔧 开发者工具
- **本地构建**: 快速本地构建脚本
- **状态监控**: 实时构建状态监控
- **Docker 支持**: 容器化构建和运行
- **配置管理**: 统一的配置文件管理

## 使用指南

### 开发者日常使用

#### 本地快速构建
```bash
# 基础构建
./build-local.sh

# 完整构建（包含测试）
./build-local.sh -t full -T -v

# 创建分发包
./build-local.sh -t dist -c
```

#### 监控构建状态
```bash
# 查看最新构建状态
./.github/scripts/monitor-builds.sh

# 实时监控
./.github/scripts/monitor-builds.sh watch

# 查看构建历史
./.github/scripts/monitor-builds.sh list -l 10
```

### CI/CD 管理

#### 手动触发构建
1. 访问 GitHub Actions 页面
2. 选择对应的工作流
3. 点击 "Run workflow"
4. 选择参数并执行

#### 发布新版本
```bash
# 创建发布标签
git tag v5.2.0
git push origin v5.2.0

# 自动触发多平台构建和发布
```

#### 配置调整
- 编辑 `.github/config/build-config.yml` 调整构建参数
- 修改 `.github/config/dependency-check-suppressions.xml` 管理安全扫描
- 更新工作流文件进行流程调整

## 故障排除和维护

### 常见问题解决

1. **构建失败**
   - 检查依赖版本兼容性
   - 验证原生库完整性
   - 查看详细构建日志

2. **安全扫描误报**
   - 更新抑制规则文件
   - 验证漏洞的实际影响
   - 升级受影响的依赖

3. **平台特定问题**
   - 检查程序集配置
   - 验证原生库路径
   - 测试平台特定功能

### 维护建议

- **定期更新**: 保持依赖和工具版本最新
- **监控性能**: 关注构建时间和资源使用
- **安全审计**: 定期审查安全扫描结果
- **文档更新**: 及时更新配置和使用文档

## 扩展和自定义

### 添加新平台支持
1. 在 `build-config.yml` 中添加平台配置
2. 创建对应的程序集配置文件
3. 添加平台特定的原生库
4. 更新工作流矩阵配置
5. 测试新平台的构建流程

### 集成新工具
1. 在工作流中添加新的作业或步骤
2. 更新配置文件添加相关参数
3. 修改构建脚本支持新功能
4. 添加相应的文档说明

这个完整的 CI/CD 系统为 tinyMediaManager 提供了企业级的构建、测试、安全扫描和发布能力，支持多平台开发和部署需求。
