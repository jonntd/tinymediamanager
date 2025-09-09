# tinyMediaManager
tinyMediaManager (https://www.tinymediamanager.org) full featured media manager to organize and clean up your media library. It is designed to allow you to create/view/edit the metadata, artwork and file structure for your media files used by Kodi (formerly XBMC), Plex, MediaPortal, Emby, Jellyfin and other compatible media center software. As a Java application it is truly cross-platform and will run on Windows, Linux and MacOS (and possibly more).

仅供学习使用。请在下载本代码后的24小时内将其删除。

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
