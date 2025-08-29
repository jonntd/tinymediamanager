# macOS 签名选项 - 完整指南

## 🎯 当前实现：GitLab 风格的专业 DMG (无签名)

我们已经实现了 GitLab 的 DMG 创建方法，但跳过了签名步骤，因为签名需要付费的 Apple 开发者账户。

### ✅ 当前功能
- 🎨 **专业 DMG 布局** - 使用 GitLab 的 create-dmg 工具
- 🖼️ **背景图片** - 专业的安装界面
- 📐 **图标布局** - 应用程序和应用程序文件夹链接
- 📦 **UDBZ 格式** - 压缩的 DMG 格式
- 🔧 **Finder 设置** - 预配置的窗口大小和位置

### ⚠️ 限制
- **无代码签名** - 需要用户手动允许
- **无 Apple 公证** - 需要绕过 Gatekeeper
- **安全警告** - macOS 会显示未知开发者警告

## 🔐 签名选项对比

### 选项1: 无签名 (当前实现)
```bash
# 用户需要执行
xattr -dr com.apple.quarantine tinyMediaManager.app
```

**优点**:
- ✅ 免费
- ✅ 立即可用
- ✅ 专业 DMG 外观

**缺点**:
- ❌ 用户需要手动操作
- ❌ 安全警告
- ❌ 不符合 macOS 最佳实践

### 选项2: Apple 开发者账户签名 (GitLab 方法)
```bash
# 需要的凭据
MAC_SIGN_CERT="Developer ID Application: Your Name (TEAM_ID)"
MAC_APPLE_ID="your-apple-id@example.com"
MAC_TEAM_ID="YOUR_TEAM_ID"
MAC_NOTARIZE_PASSWORD="app-specific-password"
```

**优点**:
- ✅ 完全可信
- ✅ 无用户操作
- ✅ 符合 macOS 标准
- ✅ 自动公证

**缺点**:
- ❌ 需要付费 ($99/年)
- ❌ 需要 Apple 开发者账户
- ❌ 复杂的设置过程

### 选项3: 自签名证书 (开发测试)
```bash
# 创建自签名证书
security create-keychain -p password build.keychain
security default-keychain -s build.keychain
security unlock-keychain -p password build.keychain

# 创建自签名证书
security add-certificates -k build.keychain cert.p12
```

**优点**:
- ✅ 免费
- ✅ 可以本地签名
- ✅ 学习签名流程

**缺点**:
- ❌ 不被 macOS 信任
- ❌ 仍需用户手动允许
- ❌ 无法公证

## 🚀 如何获得 Apple 开发者账户签名

### 步骤1: 注册 Apple 开发者账户
1. 访问 [Apple Developer](https://developer.apple.com)
2. 注册开发者账户 ($99/年)
3. 验证身份和付款信息

### 步骤2: 创建证书
1. 登录 [Apple Developer Console](https://developer.apple.com/account)
2. 进入 "Certificates, Identifiers & Profiles"
3. 创建 "Developer ID Application" 证书
4. 下载证书文件 (.cer)

### 步骤3: 创建 App 专用密码
1. 登录 [Apple ID 管理](https://appleid.apple.com)
2. 进入 "安全" 部分
3. 生成 App 专用密码
4. 记录密码 (用于公证)

### 步骤4: 配置 GitHub Secrets
```yaml
# 在 GitHub 仓库设置中添加这些 Secrets:
MAC_SIGN_CERT: "base64编码的证书文件"
MAC_APPLE_ID: "your-apple-id@example.com"
MAC_TEAM_ID: "YOUR_TEAM_ID"
MAC_NOTARIZE_PASSWORD: "app-specific-password"
MAC_CERT_PASSWORD: "证书密码"
MAC_SIGN_CERT_NAME: "Developer ID Application: Your Name (TEAM_ID)"
```

### 步骤5: 启用签名
取消注释 GitHub Actions 中的签名代码，工作流将自动检测证书并进行签名。

## 🛠️ 自签名证书创建 (测试用)

如果你想测试签名流程但不想付费，可以创建自签名证书：

### 创建自签名证书
```bash
# 1. 创建私钥
openssl genrsa -out private_key.pem 2048

# 2. 创建证书签名请求
openssl req -new -key private_key.pem -out cert_request.csr \
  -subj "/CN=Developer ID Application: Test Developer/O=Test Organization/C=US"

# 3. 创建自签名证书
openssl x509 -req -days 365 -in cert_request.csr \
  -signkey private_key.pem -out certificate.crt

# 4. 创建 PKCS#12 文件
openssl pkcs12 -export -out certificate.p12 \
  -inkey private_key.pem -in certificate.crt \
  -password pass:your_password

# 5. Base64 编码 (用于 GitHub Secrets)
base64 -i certificate.p12 -o certificate_base64.txt
```

### 配置自签名证书
```yaml
# GitHub Secrets (自签名版本):
MAC_SIGN_CERT: "base64编码的自签名证书"
MAC_CERT_PASSWORD: "your_password"
MAC_SIGN_CERT_NAME: "Developer ID Application: Test Developer"
# 注意: 自签名证书无法公证，所以不需要 Apple ID 相关设置
```

## 📋 推荐方案

### 对于个人开发者
1. **短期**: 使用当前的无签名 DMG + 用户指南
2. **长期**: 考虑购买 Apple 开发者账户

### 对于商业项目
1. **立即**: 购买 Apple 开发者账户
2. **配置**: 完整的签名和公证流程
3. **用户体验**: 无缝安装体验

### 对于开源项目
1. **当前**: 无签名 DMG + 详细用户指南
2. **考虑**: 社区赞助 Apple 开发者账户
3. **替代**: 提供多种安装方式

## 🔧 故障排除

### 常见问题
1. **"无法验证开发者"** - 使用 `xattr -dr com.apple.quarantine`
2. **"应用程序已损坏"** - 重新下载或检查文件完整性
3. **"没有权限打开"** - 检查文件权限和隔离属性

### 验证签名状态
```bash
# 检查应用签名
codesign -dv --verbose=4 tinyMediaManager.app

# 检查公证状态
spctl -a -vv tinyMediaManager.app

# 检查隔离属性
xattr -l tinyMediaManager.app
```

---

**总结**: 当前实现提供了专业的 DMG 外观和用户体验，只是需要用户手动允许应用程序运行。这是在没有 Apple 开发者账户情况下的最佳解决方案。
