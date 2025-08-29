# tinyMediaManager Docker with VNC Support

## 🎯 概述

这是增强版的 tinyMediaManager Docker 镜像，集成了 VNC 和 Web 访问支持，让你可以通过浏览器或 VNC 客户端远程访问 tinyMediaManager 的图形界面。

## 🚀 功能特性

### ✅ 核心功能
- **完整的 tinyMediaManager 应用**
- **VNC 服务器支持** (端口 5901)
- **Web VNC 客户端** (端口 6901)
- **多架构支持** (AMD64/ARM64)
- **自动化构建** (GitHub Actions)

### 🔧 技术规格
- **基础镜像**: Ubuntu 22.04
- **Java 版本**: OpenJDK 17
- **VNC 服务器**: TigerVNC
- **桌面环境**: Fluxbox (轻量级)
- **Web VNC**: noVNC 1.4.0

## 📦 可用镜像标签

| 标签 | 架构 | 描述 |
|------|------|------|
| `latest-vnc` | Multi-arch | 最新版本 (推荐) |
| `5.2.0-vnc` | Multi-arch | 特定版本 |
| `5.2.0-amd64-vnc` | AMD64 | AMD64 专用版本 |
| `5.2.0-arm64-vnc` | ARM64 | ARM64 专用版本 |

## 🏃‍♂️ 快速开始

### 基本运行
```bash
docker run -d \
  --name tinymediamanager-vnc \
  -p 5901:5901 \
  -p 6901:6901 \
  -v /path/to/your/media:/data \
  ghcr.io/jonntd/tinymediamanager:latest-vnc
```

### 使用 Docker Compose
```yaml
version: '3.8'
services:
  tinymediamanager:
    image: ghcr.io/jonntd/tinymediamanager:latest-vnc
    container_name: tinymediamanager-vnc
    ports:
      - "5901:5901"  # VNC 端口
      - "6901:6901"  # Web VNC 端口
    volumes:
      - /path/to/your/media:/data
      - tmm-config:/home/tmm/.tinyMediaManager
    environment:
      - DISPLAY=:1
    restart: unless-stopped

volumes:
  tmm-config:
```

## 🌐 访问方式

### 方式 1: Web 浏览器 (推荐)
1. 打开浏览器访问: `http://localhost:6901`
2. 默认密码: `tinymediamanager`
3. 开始使用 tinyMediaManager

### 方式 2: VNC 客户端
1. 使用任何 VNC 客户端连接到: `localhost:5901`
2. 密码: `tinymediamanager`
3. 分辨率: 1024x768

## 🔧 环境变量

| 变量名 | 默认值 | 描述 |
|--------|--------|------|
| `DISPLAY` | `:1` | X11 显示器编号 |
| `VNC_PORT` | `5901` | VNC 服务器端口 |
| `NO_VNC_PORT` | `6901` | Web VNC 端口 |
| `LANG` | `en_US.UTF-8` | 系统语言 |

## 📁 卷挂载

| 容器路径 | 描述 | 推荐挂载 |
|----------|------|----------|
| `/data` | 媒体文件目录 | 你的媒体库路径 |
| `/home/tmm/.tinyMediaManager` | 配置文件目录 | Docker 卷或本地路径 |

## 🔒 安全注意事项

### 默认密码
- VNC 密码: `tinymediamanager`
- **生产环境请修改密码**

### 修改 VNC 密码
```bash
# 进入容器
docker exec -it tinymediamanager-vnc bash

# 切换到 tmm 用户
su - tmm

# 设置新密码
vncpasswd

# 重启 VNC 服务
vncserver -kill :1
vncserver :1 -geometry 1024x768 -depth 24
```

## 🚀 与 GitLab 版本的对比

| 特性 | GitHub VNC 版本 | GitLab 版本 |
|------|----------------|-------------|
| **自动化** | ✅ 自动触发 | ❌ 手动触发 |
| **VNC 支持** | ✅ 完整支持 | ✅ 完整支持 |
| **Web 访问** | ✅ noVNC | ✅ 内置支持 |
| **基础镜像** | Ubuntu 22.04 | 自定义基础镜像 |
| **镜像大小** | 中等 | 较大 |
| **维护性** | ✅ 现代化 | ✅ 成熟稳定 |

## 🛠️ 故障排除

### 常见问题

#### 1. 无法访问 Web VNC
```bash
# 检查端口是否正确映射
docker port tinymediamanager-vnc

# 检查容器日志
docker logs tinymediamanager-vnc
```

#### 2. VNC 连接失败
```bash
# 检查 VNC 服务状态
docker exec tinymediamanager-vnc ps aux | grep vnc

# 重启 VNC 服务
docker exec tinymediamanager-vnc su - tmm -c "vncserver -kill :1 && vncserver :1"
```

#### 3. 应用无法启动
```bash
# 检查 Java 进程
docker exec tinymediamanager-vnc ps aux | grep java

# 查看应用日志
docker exec tinymediamanager-vnc tail -f /app/logs/tmm.log
```

## 📝 更新日志

### v5.2.0-vnc
- ✅ 添加 VNC 支持
- ✅ 集成 noVNC Web 客户端
- ✅ 使用 Ubuntu 22.04 基础镜像
- ✅ 自动化构建流程
- ✅ 多架构支持 (AMD64/ARM64)

## 🤝 贡献

欢迎提交 Issue 和 Pull Request 来改进这个 Docker 镜像！

## 📄 许可证

Apache License 2.0 - 详见 [LICENSE](LICENSE) 文件
