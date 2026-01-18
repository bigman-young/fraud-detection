# IntelliJ IDEA 本地调试运行指南

本指南将帮助您在 IntelliJ IDEA 中配置和运行 Flink 欺诈检测项目。

## 前置要求

1. **Java 11** - 确保已安装 Java 11 或更高版本
2. **IntelliJ IDEA** - 推荐使用 2020.1 或更高版本
3. **Maven** - IDEA 内置 Maven 即可

## 步骤 1: 导入项目

1. 打开 IntelliJ IDEA
2. 选择 `File` -> `Open` 或 `Open Project`
3. 选择 `fraud-detection-system` 目录
4. 等待 Maven 自动导入依赖（右下角会显示进度）

## 步骤 2: 配置项目 SDK

1. 打开 `File` -> `Project Structure` (快捷键: `Ctrl+Alt+Shift+S`)
2. 在 `Project` 标签页中：
   - 设置 `SDK` 为 Java 11
   - 设置 `Language level` 为 `11 - Local variable syntax for lambda parameters`
3. 在 `Modules` 标签页中，确保模块的 `Language level` 也是 11
4. 点击 `OK` 保存

## 步骤 3: 修改依赖作用域（重要）

由于 Flink 的核心依赖在 `pom.xml` 中设置为 `provided`（用于生产环境），在本地调试时需要将它们改为 `compile`。

### 方法 1: 临时修改 pom.xml（推荐用于调试）

在 `pom.xml` 中找到以下依赖，将 `scope` 从 `provided` 改为 `compile`：

```xml
<dependency>
    <groupId>org.apache.flink</groupId>
    <artifactId>flink-streaming-java</artifactId>
    <version>${flink.version}</version>
    <scope>compile</scope>  <!-- 从 provided 改为 compile -->
</dependency>

<dependency>
    <groupId>org.apache.flink</groupId>
    <artifactId>flink-clients</artifactId>
    <version>${flink.version}</version>
    <scope>compile</scope>  <!-- 从 provided 改为 compile -->
</dependency>
```

然后右键点击 `pom.xml` -> `Maven` -> `Reload Project`

### 方法 2: 使用 Maven Profile（已配置）

项目已配置 `local` profile，默认激活。如果依赖仍然缺失，请：
1. 打开 Maven 工具窗口（右侧边栏）
2. 刷新 Maven 项目
3. 确保 `local` profile 已激活

## 步骤 4: 创建运行配置

### 配置 1: Demo 模式运行（无需 Kafka，推荐用于首次测试）

1. 在 `FraudDetectionJob.java` 文件中，找到 `main` 方法
2. 点击 `main` 方法左侧的绿色运行图标，或右键点击 `main` 方法
3. 选择 `Run 'FraudDetectionJob.main()'` 或 `Debug 'FraudDetectionJob.main()'`
4. IDEA 会自动创建运行配置

**手动创建运行配置：**

1. 点击顶部工具栏 `Run` -> `Edit Configurations...`
2. 点击左上角 `+` -> 选择 `Application`
3. 配置如下：
   - **Name**: `FraudDetectionJob (Demo Mode)`
   - **Main class**: `com.hsbc.fraud.FraudDetectionJob`
   - **Use classpath of module**: `fraud-detection-system`
   - **VM options**: 
     ```
     -Dfile.encoding=UTF-8
     --add-opens java.base/java.util=ALL-UNNAMED
     --add-opens java.base/java.lang=ALL-UNNAMED
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED
     --add-opens java.base/java.text=ALL-UNNAMED
     --add-opens java.desktop/java.awt.font=ALL-UNNAMED
     ```
   - **Environment variables**: 
     ```
     USE_KAFKA=false
     ALERT_THRESHOLD=0.4
     PARALLELISM=2
     ```
   - **Working directory**: `$MODULE_DIR$`
4. 点击 `OK` 保存

### 配置 2: Kafka 模式运行（需要本地 Kafka）

如果需要使用 Kafka 模式：

1. 确保本地已启动 Kafka（可以使用项目中的 `docker-compose.yaml`）
2. 创建新的运行配置：
   - **Name**: `FraudDetectionJob (Kafka Mode)`
   - **Main class**: `com.hsbc.fraud.FraudDetectionJob`
   - **Use classpath of module**: `fraud-detection-system`
   - **VM options**: 
     ```
     -Dfile.encoding=UTF-8
     --add-opens java.base/java.util=ALL-UNNAMED
     --add-opens java.base/java.lang=ALL-UNNAMED
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED
     --add-opens java.base/java.text=ALL-UNNAMED
     --add-opens java.desktop/java.awt.font=ALL-UNNAMED
     ```
   - **Environment variables**: 
     ```
     USE_KAFKA=true
     KAFKA_BOOTSTRAP_SERVERS=localhost:9092
     INPUT_TOPIC=transactions
     ALERT_TOPIC=fraud-alerts
     ALERT_THRESHOLD=0.4
     PARALLELISM=2
     ```
   - **Working directory**: `$MODULE_DIR$`

## 步骤 5: 启动本地 Kafka（可选）

如果使用 Kafka 模式，需要先启动 Kafka：

```bash
# 在项目根目录执行
docker-compose up -d
```

或者使用项目提供的脚本：
```bash
cd fraud-detection-system
docker-compose up -d
```

## 步骤 6: 运行和调试

### 运行
1. 选择创建的运行配置
2. 点击绿色运行按钮或按 `Shift+F10`

### 调试
1. 在代码中设置断点（点击行号左侧）
2. 选择运行配置
3. 点击调试按钮或按 `Shift+F9`
4. 程序会在断点处暂停，可以查看变量、单步执行等

## 常见问题

### 1. ClassNotFoundException 或 NoClassDefFoundError

**原因**: Flink 核心依赖未正确加载

**解决方案**:
- 检查 `pom.xml` 中 `flink-streaming-java` 和 `flink-clients` 的 scope 是否为 `compile`
- 右键 `pom.xml` -> `Maven` -> `Reload Project`
- 在 Maven 工具窗口中点击刷新按钮

### 2. 找不到主类

**原因**: 项目未正确编译

**解决方案**:
- `Build` -> `Rebuild Project`
- 检查 `File` -> `Project Structure` -> `Modules` 中是否正确识别了源代码目录

### 3. 端口被占用

**原因**: Flink 或 Kafka 端口已被占用

**解决方案**:
- 检查是否有其他 Flink 或 Kafka 实例在运行
- 修改环境变量中的端口配置

### 4. 日志输出混乱

**原因**: Log4j 配置问题

**解决方案**:
- 检查 `src/main/resources/log4j2.xml` 是否存在
- 确保日志配置文件在 classpath 中

### 5. InaccessibleObjectException 错误

**错误信息**: 
```
java.lang.reflect.InaccessibleObjectException: Unable to make field private final java.lang.Object[] java.util.Arrays$ArrayList.a accessible: module java.base does not "opens java.util" to unnamed module
```

**原因**: Java 9+ 模块系统阻止了 Flink 的 Kryo 序列化库通过反射访问 JDK 内部类

**解决方案**:
- 在运行配置的 **VM options** 中添加以下参数：
  ```
  --add-opens java.base/java.util=ALL-UNNAMED
  --add-opens java.base/java.lang=ALL-UNNAMED
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED
  --add-opens java.base/java.text=ALL-UNNAMED
  --add-opens java.desktop/java.awt.font=ALL-UNNAMED
  ```
- 这些参数允许未命名模块访问 JDK 内部包，这是 Flink 序列化所必需的

## 调试技巧

1. **设置断点**: 在关键处理逻辑处设置断点，如 `FraudDetectionProcessor` 和 `VelocityCheckProcessor`
2. **查看数据流**: 在 `processTransactions` 方法中设置断点，查看交易数据
3. **监控状态**: 在状态操作处设置断点，查看 Flink 状态管理
4. **条件断点**: 右键断点可以设置条件，例如只在特定账户 ID 时暂停

## 环境变量说明

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `USE_KAFKA` | `false` | 是否使用 Kafka 模式 |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka 服务器地址 |
| `INPUT_TOPIC` | `transactions` | 输入 Kafka Topic |
| `ALERT_TOPIC` | `fraud-alerts` | 输出 Kafka Topic |
| `ALERT_THRESHOLD` | `0.4` | 欺诈检测阈值 |
| `PARALLELISM` | `4` | Flink 并行度 |

## 下一步

- 查看 `README.md` 了解项目整体架构
- 运行单元测试：`Run` -> `Run All Tests`
- 查看 Flink Web UI（如果配置了）：`http://localhost:8081`
