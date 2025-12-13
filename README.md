# tinyMediaManager
tinyMediaManager (https://www.tinymediamanager.org) full featured media manager to organize and clean up your media library. It is designed to allow you to create/view/edit the metadata, artwork and file structure for your media files used by Kodi (formerly XBMC), Plex, MediaPortal, Emby, Jellyfin and other compatible media center software. As a Java application it is truly cross-platform and will run on Windows, Linux and MacOS (and possibly more).

仅供学习使用。请在下载本代码后的24小时内将其删除。
[![zread](https://img.shields.io/badge/Ask_Zread-_.svg?style=flat&color=00b0aa&labelColor=000000&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB3aWR0aD0iMTYiIGhlaWdodD0iMTYiIHZpZXdCb3g9IjAgMCAxNiAxNiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTQuOTYxNTYgMS42MDAxSDIuMjQxNTZDMS44ODgxIDEuNjAwMSAxLjYwMTU2IDEuODg2NjQgMS42MDE1NiAyLjI0MDFWNC45NjAxQzEuNjAxNTYgNS4zMTM1NiAxLjg4ODEgNS42MDAxIDIuMjQxNTYgNS42MDAxSDQuOTYxNTZDNS4zMTUwMiA1LjYwMDEgNS42MDE1NiA1LjMxMzU2IDUuNjAxNTYgNC45NjAxVjIuMjQwMUM1LjYwMTU2IDEuODg2NjQgNS4zMTUwMiAxLjYwMDEgNC45NjE1NiAxLjYwMDFaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00Ljk2MTU2IDEwLjM5OTlIMi4yNDE1NkMxLjg4ODEgMTAuMzk5OSAxLjYwMTU2IDEwLjY4NjQgMS42MDE1NiAxMS4wMzk5VjEzLjc1OTlDMS42MDE1NiAxNC4xMTM0IDEuODg4MSAxNC4zOTk5IDIuMjQxNTYgMTQuMzk5OUg0Ljk2MTU2QzUuMzE1MDIgMTQuMzk5OSA1LjYwMTU2IDE0LjExMzQgNS42MDE1NiAxMy43NTk5VjExLjAzOTlDNS42MDE1NiAxMC42ODY0IDUuMzE1MDIgMTAuMzk5OSA0Ljk2MTU2IDEwLjM5OTlaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik0xMy43NTg0IDEuNjAwMUgxMS4wMzg0QzEwLjY4NSAxLjYwMDEgMTAuMzk4NCAxLjg4NjY0IDEwLjM5ODQgMi4yNDAxVjQuOTYwMUMxMC4zOTg0IDUuMzEzNTYgMTAuNjg1IDUuNjAwMSAxMS4wMzg0IDUuNjAwMUgxMy43NTg0QzE0LjExMTkgNS42MDAxIDE0LjM5ODQgNS4zMTM1NiAxNC4zOTg0IDQuOTYwMVYyLjI0MDFDMTQuMzk4NCAxLjg4NjY0IDE0LjExMTkgMS42MDAxIDEzLjc1ODQgMS42MDAxWiIgZmlsbD0iI2ZmZiIvPgo8cGF0aCBkPSJNNCAxMkwxMiA0TDQgMTJaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00IDEyTDEyIDQiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIxLjUiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIvPgo8L3N2Zz4K&logoColor=ffffff)](https://zread.ai/jonntd/tinymediamanager)
1. Clone this repository to your computer

   ```bash
   git clone https://gitlab.com/tinyMediaManager/tinyMediaManager.git
   ```

2. Build using maven

   ```bash
   mvn package
   ```

## 修改功能
### 1. 增加ai辅助刮削，使用gemini的免费带联网搜索的模型 gemini-2.5-flash-lite-search ，也可以用其他兼容openai格式的api

 ```bash
    https://github.com/snailyp/gemini-balance      
   ```

![alt text](image/WX20250910-021240@2x.png)

### 2. 增加设置关闭添加视频文件tmm打开文件扫描视频信息，适用于cd2挂载网盘的情况
![alt text](image/WX20250910-022231@2x.png)
![alt text](image/WX20250910-022247@2x.png)

### 3. 刮削窗口增加 “ai fix”按钮 使用ai识别电影名，右键批量刮削匹配默认使用的AI刮削。
![alt text](image/WX20250910-022432@2x.png)

### 4. 默认刮削海报会下载到视频目录，增加一个选项让图片下载到本地缓存目录，避免图片上传到cd2挂载的网盘
![alt text](image/WX20250910-022926@2x.png)


[配置示例](示例配置)
