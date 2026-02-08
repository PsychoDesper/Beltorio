# Beltorio - 传送带模组

一个为 Minecraft 1.21.1 (Fabric) 打造的传送带模组，灵感来自 Factorio。

## 功能

- **传送带方块**：放置后可将物品和实体沿指定方向推动
- **方向控制**：传送带朝向与玩家放置时的面向相反
- **速度限制**：内置最大速度上限，防止无限加速
- **支持水淹**：传送带可以放置在水中
- **动画材质**：带有箭头方向指示的动态贴图

## 安装要求

- Minecraft 1.21.1
- Fabric Loader >= 0.15.0
- Fabric API

## 开发环境搭建

### 前置要求

- JDK 21（推荐 [Eclipse Adoptium](https://adoptium.net/temurin/releases/?version=21)）

### 构建

```bash
gradlew.bat build
```

构建产物位于 `build/libs/beltorio-1.0.0.jar`

### 启动测试客户端

```bash
gradlew.bat runClient
```

## 使用方法

1. 创建一个**创造模式**世界
2. 打开物品栏，找到**红石**标签页
3. 选择 **Conveyor Belt**（传送带）
4. 将多个传送带排成一排
5. 丢物品到传送带上，观察物品自动移动

## 项目结构

```
src/main/java/com/beltorio/
├── Beltorio.java               # 主初始化器，注册方块/物品/方块实体
├── BeltorioClient.java          # 客户端初始化器，渲染层设置
└── block/
    ├── ConveyorBeltBlock.java   # 传送带方块逻辑
    └── entity/
        └── ConveyorBeltBlockEntity.java  # 方块实体（预留扩展）
```

## 许可证

MIT
