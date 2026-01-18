# Java 模块系统问题快速修复指南

## 问题描述

运行时出现以下错误：
```
java.lang.reflect.InaccessibleObjectException: Unable to make field private final java.lang.Object[] java.util.Arrays$ArrayList.a accessible: module java.base does not "opens java.util" to unnamed module
```

## 原因

Java 9+ 引入了模块系统，默认不允许反射访问 JDK 内部类。Flink 使用的 Kryo 序列化库需要通过反射访问这些类。

## 解决方案

### 方法 1: IntelliJ IDEA 运行配置（推荐）

1. 打开运行配置：`Run` -> `Edit Configurations...`
2. 选择或创建 `FraudDetectionJob` 运行配置
3. 在 **VM options** 字段中添加以下内容：

```
-Dfile.encoding=UTF-8
--add-opens java.base/java.util=ALL-UNNAMED
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.lang.reflect=ALL-UNNAMED
--add-opens java.base/java.text=ALL-UNNAMED
--add-opens java.desktop/java.awt.font=ALL-UNNAMED
```

4. 点击 `OK` 保存
5. 重新运行程序

### 方法 2: 使用 Maven Exec 插件

在项目根目录执行：

```bash
mvn clean compile exec:java -Dexec.mainClass="com.hsbc.fraud.FraudDetectionJob"
```

Maven Exec 插件已配置了必要的 JVM 参数。

### 方法 3: 命令行运行（如果已打包）

```bash
java --add-opens java.base/java.util=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
     --add-opens java.base/java.text=ALL-UNNAMED \
     --add-opens java.desktop/java.awt.font=ALL-UNNAMED \
     -jar target/fraud-detection-system-1.0.0.jar
```

## 参数说明

- `--add-opens`: 允许未命名模块访问指定包的内部 API
- `java.base/java.util=ALL-UNNAMED`: 允许所有未命名模块访问 `java.util` 包
- 其他参数类似，用于开放 Flink 序列化所需的其他包

## 验证

运行后应该不再出现 `InaccessibleObjectException` 错误，程序可以正常处理交易数据。

## 注意事项

- 这些参数仅用于本地开发和调试
- 生产环境部署时，通常通过 Flink 集群配置来设置这些参数
- 如果使用 Docker 或 Kubernetes 部署，需要在相应的配置文件中添加这些 JVM 参数
