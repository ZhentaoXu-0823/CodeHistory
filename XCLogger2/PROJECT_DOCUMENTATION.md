# XcLogger 项目文档

## 项目概述

XcLogger 是一个Android日志记录应用，用于捕获系统日志并提供配置管理功能。项目采用模块化架构，实现了日志捕获、文件管理、流程控制和用户界面等核心功能。

## 功能需求

### 核心功能
- **日志记录系统**：捕获系统日志并存储到文件
- **配置管理**：从XML加载默认配置，支持数据库持久化
- **文件管理**：日志文件创建、轮转、历史记录
- **流程控制**：监控所有操作并记录到历史文件
- **用户界面**：简洁扁平化的配置和状态显示
- **开发者工具**：隐藏的调试功能

### 技术规格
- **日志存储路径**：`/storage/emulated/0/sdcard/XcLogger`
- **文件命名规则**：`main_log_yyyyMMdd_HHmmss.txt`
- **单文件大小限制**：4MB（可配置）
- **缓冲区大小**：4096字节（可配置）
- **历史记录文件**：`XcLoggerOperateHistory.txt`
- **配置存储**：SharedPreferences
- **最低Android版本**：API 29 (Android 10)
- **目标Android版本**：API 33 (Android 13)

## 项目结构

```
XcLogger/
 app/
    src/main/
       java/com/xcheng/xclogger/
          MainActivity.java                    # 主界面控制器
          filemanager/
             FileManager.java                # 文件管理模块
          processctr/
             ConfigLoader.java               # 配置加载器
             DeveloperActivity.java          # 开发者调试页面
             ProcessController.java          # 流程控制器
          recorder/
             LogBuffer.java                  # 日志缓冲区
             SystemLogCatcher.java           # 系统日志捕获器
          ui/
             XcLoggerConfigActivity.java     # 配置界面
          util/
              XcLoggerConfig.java             # 配置数据模型
              XcLoggerDatabase.java           # 数据库工具类
       res/
          drawable/                           # 图标资源
             bg_rounded_gray.xml            # 圆角背景
             ic_start.xml                   # 开始图标
             ic_stop.xml                    # 停止图标
          layout/                             # 布局文件
             activity_main.xml              # 主界面布局
          values/                             # 字符串资源
             strings.xml                    # 字符串定义
             colors.xml                     # 颜色定义
             themes.xml                     # 主题定义
          xml/
              default_config.xml              # 默认配置文件
       AndroidManifest.xml                     # 应用清单
    build.gradle                                # 应用构建配置
 build.gradle                                    # 项目构建配置
 settings.gradle                                 # 项目设置
 gradle.properties                               # Gradle属性
```

## 类和方法详细说明

### 1. MainActivity.java
**包路径**：`com.xcheng.xclogger`
**作用**：主界面控制器，提供用户交互入口

**属性**：
- `txtTitle` (TextView) - 应用标题显示
- `btnDetail` (TextView) - 详情按钮
- `txtState` (TextView) - 状态文本显示
- `txtPath` (TextView) - 路径显示
- `imgState` (ImageView) - 状态图标
- `btnStart` (LinearLayout) - 开始/停止按钮容器
- `running` (boolean) - 运行状态标志
- `config` (XcLoggerConfig) - 配置对象
- `database` (XcLoggerDatabase) - 数据库操作对象
- `titleTapCount` (int) - 标题点击计数
- `lastTapTs` (long) - 上次点击时间戳

**方法列表**：
- `onCreate(Bundle savedInstanceState)` - 初始化UI组件和配置
  - 设置布局文件
  - 初始化UI组件引用
  - 加载配置和运行状态
  - 设置点击监听器
- `toggleState()` - 切换日志记录状态
  - 切换running标志
  - 保存状态到数据库
  - 更新UI显示
- `updateStateUi()` - 更新界面显示状态
  - 根据running状态设置图标和文本
  - 运行中显示开始图标和"XcLogger is Running"
  - 停止时显示停止图标和"XcLogger has stopped"
- `editPath()` - 编辑日志存储路径
  - 创建输入对话框
  - 允许用户修改日志存储路径
  - 保存修改到配置和数据库
- `handleSecretTap()` - 处理标题点击事件（开发者入口）
  - 检测连续5次点击（1秒内）
  - 显示密码输入对话框
  - 密码"0000"进入开发者模式

### 2. FileManager.java (filemanager包)
**包路径**：`com.xcheng.xclogger.filemanager`
**作用**：文件管理模块，负责日志文件的创建、写入和轮转

**属性**：
- `context` (Context) - Android上下文
- `baseDir` (File) - 基础目录
- `currentMainLogFile` (File) - 当前主日志文件
- `historyFile` (File) - 操作历史文件
- `config` (XcLoggerConfig) - 配置对象

**方法列表**：
- `FileManager(Context ctx)` - 构造函数
  - 初始化上下文和配置
  - 设置基础目录路径
  - 创建历史文件引用
- `ensureBaseDir()` - 确保基础目录存在
  - 检查目录是否存在
  - 不存在则创建目录
  - 返回创建结果
- `createNewMainLogFile()` - 创建新的主日志文件
  - 确保基础目录存在
  - 生成时间戳文件名
  - 格式：`main_log_yyyyMMdd_HHmmss.txt`
  - 创建新文件
- `appendToMainLog(byte[] data, int len)` - 追加数据到主日志文件
  - 检查文件是否存在
  - 追加数据到文件末尾
  - 刷新输出流
  - 检查是否需要轮转
- `appendOperateHistory(String operation)` - 追加操作历史记录
  - 确保基础目录存在
  - 追加操作记录到历史文件
  - 使用UTF-8编码
- `rotateIfNeeded(int additionalBytes)` - 检查并执行文件轮转
  - 计算当前文件大小
  - 检查是否超过限制
  - 超过则创建新文件
- `getCurrentMainLogFile()` - 获取当前主日志文件
- `getBaseDir()` - 获取基础目录

### 3. ConfigLoader.java (processctr包)
**包路径**：`com.xcheng.xclogger.processctr`
**作用**：配置加载器，从XML和数据库加载配置

**属性**：
- `CURRENT` (static volatile XcLoggerConfig) - 当前配置实例

**方法列表**：
- `load(Context ctx)` - 加载配置（优先数据库，回退XML）
  - 尝试从数据库加载配置
  - 数据库为空则解析XML
  - 解析成功则保存到数据库
  - 设置全局配置实例
- `replaceWith(XcLoggerConfig cfg)` - 替换当前配置实例
  - 静态方法，更新全局配置
- `current()` - 获取当前配置实例
  - 静态方法，返回全局配置
- `parseXml(Context ctx)` - 解析XML配置文件
  - 解析`res/xml/default_config.xml`
  - 提取各种配置参数
  - 解析过滤规则配置
  - 返回配置对象
- `parseFilterBlock(XmlResourceParser parser, XcLoggerConfig config)` - 解析过滤规则配置
  - 解析标签过滤
  - 解析级别过滤
  - 解析包名过滤
  - 设置默认值

### 4. DeveloperActivity.java (processctr包)
**包路径**：`com.xcheng.xclogger.processctr`
**作用**：开发者调试页面，提供数据库信息查看

**方法列表**：
- `onCreate(Bundle savedInstanceState)` - 初始化调试界面
  - 创建简单的按钮界面
  - 设置按钮点击监听器
- `printDb()` - 打印数据库配置信息到日志
  - 加载数据库配置
  - 打印配置参数到LogCat
  - 用于调试和诊断

### 5. ProcessController.java (processctr包)
**包路径**：`com.xcheng.xclogger.processctr`
**作用**：流程控制器，协调各模块工作并记录操作历史

**属性**：
- `instance` (static ProcessController) - 单例实例
- `fileManager` (FileManager) - 文件管理器
- `systemLogCatcher` (SystemLogCatcher) - 日志捕获器
- `context` (Context) - Android上下文
- `isRunning` (boolean) - 运行状态

**方法列表**：
- `ProcessController(Context ctx)` - 私有构造函数
  - 初始化上下文
  - 创建文件管理器
  - 创建日志捕获器
- `getInstance(Context ctx)` - 获取单例实例
  - 线程安全的单例模式
  - 懒加载初始化
- `startLogging()` - 启动日志记录流程
  - 检查是否已在运行
  - 验证配置可用性
  - 确保基础目录存在
  - 创建新的主日志文件
  - 设置日志行监听器
  - 启动系统日志捕获
  - 记录操作历史
- `stopLogging()` - 停止日志记录流程
  - 停止日志捕获器
  - 更新运行状态
  - 记录操作历史
- `appendOperateSafe(String operation)` - 安全地追加操作记录
  - 添加时间戳
  - 格式化日志条目
  - 追加到历史文件
  - 异常安全处理
- `getFileManager()` - 获取文件管理器实例
- `getSystemLogCatcher()` - 获取日志捕获器实例
- `isRunning()` - 检查是否正在运行

### 6. LogBuffer.java (recorder包)
**包路径**：`com.xcheng.xclogger.recorder`
**作用**：日志缓冲区，管理内存中的日志数据

**属性**：
- `buffer` (StringBuilder) - 缓冲区内容
- `maxSize` (int) - 最大缓冲区大小
- `flushListener` (OnFlushListener) - 刷新监听器

**接口**：
- `OnFlushListener` - 刷新监听器接口
  - `onFlush(String data)` - 刷新时回调

**方法列表**：
- `LogBuffer()` - 构造函数
  - 从配置获取缓冲区大小
  - 默认4096字节
  - 初始化StringBuilder
- `addLogLine(String line)` - 添加日志行到缓冲区
  - 检查行是否为空
  - 处理超大行（直接刷新）
  - 检查缓冲区容量
  - 自动刷新机制
- `flush()` - 刷新缓冲区数据
  - 通知监听器
  - 清空缓冲区
- `isFull()` - 检查缓冲区是否已满
- `getUsedLength()` - 获取已使用长度
- `setOnFlushListener(OnFlushListener listener)` - 设置刷新监听器
- `getMaxSize()` - 获取缓冲区最大大小
- `clear()` - 清空缓冲区

### 7. SystemLogCatcher.java (recorder包)
**包路径**：`com.xcheng.xclogger.recorder`
**作用**：系统日志捕获器，执行logcat命令并处理输出

**属性**：
- `logcatProcess` (Process) - logcat进程
- `executor` (ExecutorService) - 单线程执行器
- `running` (AtomicBoolean) - 运行状态（线程安全）
- `logLineListener` (OnLogLineListener) - 日志行监听器
- `logBuffer` (LogBuffer) - 日志缓冲区

**接口**：
- `OnLogLineListener` - 日志行监听器接口
  - `onLogLine(String line)` - 接收日志行

**方法列表**：
- `SystemLogCatcher()` - 构造函数
  - 创建日志缓冲区
  - 创建单线程执行器
  - 设置缓冲区刷新监听器
- `start()` - 启动日志捕获
  - 检查是否已在运行
  - 构建logcat命令
  - 启动logcat进程
  - 在后台线程读取输出
- `stop()` - 停止日志捕获
  - 设置停止标志
  - 销毁logcat进程
  - 刷新缓冲区
- `isRunning()` - 检查运行状态
- `setOnLogLineListener(OnLogLineListener listener)` - 设置日志行监听器
- `buildLogcatCommand(XcLoggerConfig config)` - 构建logcat命令
  - 添加标签过滤
  - 添加级别过滤
  - 添加包名过滤
  - 添加时间戳格式
- `readLogcatOutput()` - 读取logcat输出
  - 在后台线程中执行
  - 逐行读取输出
  - 添加到缓冲区
  - 异常处理
- `getLogBuffer()` - 获取缓冲区实例

### 8. XcLoggerConfigActivity.java (ui包)
**包路径**：`com.xcheng.xclogger.ui`
**作用**：配置界面，提供参数编辑功能

**属性**：
- `etTotal` (EditText) - 总大小输入框
- `etFile` (EditText) - 文件大小输入框
- `etBuffer` (EditText) - 缓冲区大小输入框
- `etDir` (EditText) - 目录路径输入框
- `etPeriod` (EditText) - 保存周期输入框
- `etTag` (EditText) - 标签过滤输入框
- `etLevel` (EditText) - 级别过滤输入框
- `etPkg` (EditText) - 包名过滤输入框
- `cfg` (XcLoggerConfig) - 配置对象

**方法列表**：
- `onCreate(Bundle savedInstanceState)` - 初始化配置界面
  - 创建动态布局
  - 设置顶部固定区域（标题+保存按钮）
  - 创建滚动详情区域
  - 添加各种配置输入框
  - 设置保存按钮监听器
- `addRow(LinearLayout parent, String label, String value)` - 创建普通标签+输入行
  - 创建水平布局行
  - 添加标签和输入框
  - 设置布局权重
  - 返回输入框引用
- `addRowWithUnit(LinearLayout parent, String label, String value, String unit, boolean numeric)` - 创建带单位的标签+输入行
  - 创建水平布局行
  - 添加标签、输入框和单位标签
  - 设置数字输入类型
  - 设置布局权重
  - 返回输入框引用
- `save()` - 保存所有配置到数据库并更新全局缓存
  - 从输入框获取值
  - 更新配置对象
  - 保存到数据库
  - 更新全局配置
- `safeInt(String s)` - 安全字符串转整数
  - 尝试解析整数
  - 解析失败返回0
  - 异常安全处理

### 9. XcLoggerConfig.java (util包)
**包路径**：`com.xcheng.xclogger.util`
**作用**：配置数据模型，存储所有配置参数

**属性**：
- `totalSizeGb` (int) - 总大小限制（GB）
- `fileSizeMb` (int) - 单文件大小限制（MB）
- `bufferSizeBytes` (int) - 缓冲区大小（字节）
- `logDir` (String) - 日志存储目录
- `logPeriodHours` (int) - 日志保存周期（小时）
- `filterTag` (String) - 过滤标签
- `filterLevel` (String) - 过滤级别
- `filterPackage` (String) - 过滤包名

**方法列表**：
- 所有属性都有对应的getter和setter方法
- 标准的JavaBean模式
- 支持序列化和反序列化

### 10. XcLoggerDatabase.java (util包)
**包路径**：`com.xcheng.xclogger.util`
**作用**：数据库工具类，管理SharedPreferences存储

**常量**：
- `PREF` - SharedPreferences文件名
- `K_TOTAL` - 总大小键名
- `K_FILE` - 文件大小键名
- `K_BUFFER` - 缓冲区大小键名
- `K_DIR` - 目录路径键名
- `K_PERIOD` - 保存周期键名
- `K_TAG` - 标签过滤键名
- `K_LEVEL` - 级别过滤键名
- `K_PKG` - 包名过滤键名
- `K_RUNNING` - 运行状态键名

**属性**：
- `sp` (SharedPreferences) - SharedPreferences实例

**方法列表**：
- `XcLoggerDatabase(Context ctx)` - 构造函数
  - 获取SharedPreferences实例
- `saveConfig(XcLoggerConfig c)` - 保存配置到数据库
  - 获取编辑器
  - 保存所有配置参数
  - 异步提交
- `loadConfig()` - 从数据库加载配置
  - 检查是否有配置数据
  - 创建配置对象
  - 加载所有参数
  - 返回配置对象
- `saveRunningState(boolean running)` - 保存运行状态
- `loadRunningState()` - 加载运行状态

## 模块职责分工

### recorder包 - 日志记录模块
- **LogBuffer**：内存缓冲区管理，优化I/O性能，支持自动刷新
- **SystemLogCatcher**：系统日志捕获，执行logcat命令，异步处理输出

### filemanager包 - 文件管理模块
- **FileManager**：文件操作和轮转，管理存储空间，记录操作历史

### processctr包 - 流程控制模块
- **ProcessController**：流程协调，监控操作，单例模式管理
- **ConfigLoader**：配置管理，加载和缓存，支持热更新
- **DeveloperActivity**：调试工具，开发支持，数据库信息查看

### ui包 - 用户界面模块
- **XcLoggerConfigActivity**：配置界面，参数编辑，动态布局生成

### util包 - 工具模块
- **XcLoggerConfig**：数据模型，配置载体，JavaBean模式
- **XcLoggerDatabase**：持久化存储，数据管理，SharedPreferences封装

## 数据流图

```
用户操作  MainActivity  ProcessController  SystemLogCatcher
                                    
                              LogBuffer  FileManager  文件系统
                                    
                              操作历史记录
```

## 配置流程

```
应用启动  ConfigLoader.load()  XcLoggerDatabase.loadConfig()
                                    
                              数据库为空？
                                     是
                              parseXml()  保存到数据库
                                    
                              返回配置对象
```

## 开发规范

### 注释规范
- 类顶部：包含类的作用描述和所有方法简介
- 方法顶部：包含参数说明和返回值说明
- 使用多行注释格式：`/* ... */`

### 编码规范
- 包名：`com.xcheng.xclogger`
- 类名：使用PascalCase
- 方法名：使用camelCase
- 常量：使用UPPER_SNAKE_CASE
- 属性：使用camelCase

### 异常处理
- 所有文件操作使用try-catch包装
- 提供安全的默认值
- 记录错误日志
- 静默处理非关键异常

### 线程安全
- 使用AtomicBoolean保证状态一致性
- 单例模式使用synchronized
- 后台任务使用ExecutorService

## 测试建议

### 功能测试
- 日志记录功能测试
- 配置保存和加载测试
- 文件轮转测试
- UI交互测试
- 开发者模式测试

### 性能测试
- 内存使用情况
- 文件I/O性能
- 缓冲区效率
- 长时间运行稳定性

### 兼容性测试
- 不同Android版本（API 29-33）
- 不同设备存储
- 权限处理
- 不同屏幕尺寸

## 部署说明

### 权限要求
- `READ_EXTERNAL_STORAGE` - 读取外部存储
- `WRITE_EXTERNAL_STORAGE` - 写入外部存储

### 最低要求
- Android API 29+ (Android 10)
- 外部存储访问权限
- 日志读取权限

### 构建配置
- 编译SDK：33
- 目标SDK：33
- 最低SDK：29
- Java版本：1.8

## 维护指南

### 日志文件管理
- 定期清理过期日志
- 监控存储空间使用
- 检查文件权限
- 验证文件完整性

### 配置管理
- 备份重要配置
- 版本升级兼容性
- 默认值更新
- 配置验证

### 性能优化
- 缓冲区大小调优
- 文件轮转策略
- 内存使用监控
- 线程池管理

### 故障排除
- 检查权限设置
- 验证存储空间
- 查看操作历史
- 使用开发者模式诊断

## 版本信息

- **当前版本**：1.0
- **版本代码**：1
- **构建工具**：Gradle 8.1.3
- **Android Gradle Plugin**：8.1.3
- **目标框架**：Android 13 (API 33)
