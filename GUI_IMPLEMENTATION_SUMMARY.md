# ME 线缆放置工具 GUI 实现总结

## 1. 项目目标
将 `ME Placement Tool` 中的线缆放置工具（原径向菜单）替换为符合 Applied Energistics 2 (AE2) 设计风格的容器化 GUI。

## 2. 核心架构
基于 AE2 的 GUI 框架实现，采用 Client-Server 架构：
- **Server (Container)**: `CableToolMenu extends AEBaseMenu`
- **Client (Screen)**: `CableToolScreen extends AEBaseScreen<CableToolMenu>`
- **Style**: JSON 驱动的样式定义 (`cable_tool.json`)

## 3. 已完成修改

### 3.1 新增文件

#### `src/main/java/com/moakiee/meplacementtool/CableToolMenu.java`
- **功能**: 服务器端菜单逻辑。
- **关键点**:
  - `upgradeInv`: 一个 1 格的内部库存，用于存放升级卡。
  - `KeyOfSpectrumSlot`: 自定义槽位类，仅接受 `MEPlacementToolMod.KEY_OF_SPECTRUM`。
  - `loadSettings()` / `onUpgradeChanged()`: 处理 NBT 数据与 GUI 状态的同步。
  - `@GuiSync` 字段: `currentMode`, `currentCableType`, `currentColor`, `hasUpgrade` 用于自动同步状态到客户端。
  - `registerClientAction()`: 处理客户端发来的模式/颜色/线缆更改请求。

#### `src/main/java/com/moakiee/meplacementtool/client/CableToolScreen.java`
- **功能**: 客户端屏幕渲染。
- **关键点**:
  - **动态布局**:
    - **无升级**: 显示 "线缆选择" | "模式选择"。
    - **有升级**: 显示 "颜色选择" (顶) -> "线缆选择" (下左) | "模式选择" (下右)。
  - **组件绘制**:
    - `UpgradesPanelSimple`: 独立的升级槽面板组件，位于 GUI 右上角。
    - `drawColorSection`: 绘制 9列 x 2行 的颜色选择块 (10px)。
    - `drawCableSection`: 绘制 3列 x 2行 的线缆图标按钮。
    - `drawModeSection`: 绘制单选按钮风格的模式选择。
  - **交互**: 处理鼠标点击和悬停提示 (Tooltip/Hint Text)。
  - **兼容性**: 修复了 Minecraft 1.20.x 中 `Slot.x/y` 为 final 导致无法代码动态设置槽位的问题，改用 JSON 静态配置 + 自定义渲染偏移（如果需要）或纯 JSON 布局。

#### `src/main/resources/assets/meplacementtool/screens/cable_tool.json`
- **功能**: 定义 GUI 的基础结构和槽位位置。
- **内容**:
  - 引用 AE2 的 `common.json` 和 `player_inventory.json`。
  - 背景使用 `ae2:guis/priority.png` (简洁背景)。
  - 定义 `PLAYER_INVENTORY` 和 `PLAYER_HOTBAR` 槽位位置。
  - 定义 `CABLE_TOOL_UPGRADE` (升级槽) 位置在右上角。
  - 定义标题文本位置。

### 3.2 修改文件

#### `src/main/java/com/moakiee/meplacementtool/ItemMECablePlacementTool.java`
- **修改**:
  - 移除了 Shift+右键 插入/移除升级卡的旧逻辑。
  - 修改 `use()` 方法：空手右键时调用 `openCableToolMenu()` 打开新 GUI。
  - 添加 `openCableToolMenu()` 方法，使用 `MenuLocator` 打开菜单。

#### `src/main/java/com/moakiee/meplacementtool/ModMenus.java`
- **修改**: 注册了新的菜单类型 `CABLE_TOOL_MENU`。

#### `src/main/java/com/moakiee/meplacementtool/MEPlacementToolMod.java`
- **修改**: 在 `onClientSetup` 中注册了 `CableToolScreen`。使用了 AE2 的 `StyleManager` 加载样式。

#### 语言文件 (`en_us.json`, `zh_cn.json`)
- **修改**: 添加了 GUI 标题、颜色、线缆类型、模式以及升级状态的本地化键。

## 4. 技术细节与注意事项

### 依赖关系
- 强依赖 `Applied-Energistics-2` 的 API 和资源。
- 背景纹理直接复用 AE2 资源 (`ae2:guis/priority.png`)。

### 槽位位置问题解决
在 Minecraft 1.20.x (Forge) 中，`Slot.x` 和 `Slot.y` 是 `final` 的。
- **解决方案**: 在 `cable_tool.json` 中明确定义 `CABLE_TOOL_UPGRADE` 的位置 `(left: 152, top: 4)`，而不是在 `updateBeforeRender` 中动态计算赋值。

### 升级槽逻辑
- 升级槽是 `CableToolMenu` 的一部分，但其 GUI 表现通过 `CableToolScreen` 中的 `UpgradesPanelSimple` 辅助（虽然主要位置由 JSON 控制，但在一些复杂交互中可能需要额外处理）。当前实现主要依赖 JSON 定位。

## 5. 接手工作建议 / 下一步

1.  **游戏内测试**:
    - 验证空手右键能否打开 GUI。
    - 验证放入/取出 "光谱钥匙" (Key of Spectrum) 是否正确触发 GUI 布局变化（2部分 <-> 3部分）。
    - 验证点击颜色、线缆、模式是否即时更新工具 NBT 并同步回客户端。
    - 检查玩家物品栏交互是否正常（Shift-click 等）。

2.  **视觉微调**:
    - 如果升级槽位置需要微调，修改 `cable_tool.json`。
    - 如果颜色块或按钮大小觉得不合适，修改 `CableToolScreen.java` 中的常量。

3.  **功能扩展**:
    - 目前是基于 NBT 同步的，如果将来需要支持更复杂的配置保存（如内存卡），可能需要扩展 `CableToolMenu` 的同步逻辑。

4.  **已知限制**:
    - 背景图使用的是 AE2 的 `priority.png`，其尺寸固定。如果需要更大的自定义布局，可能需要制作专门的 `.png` 背景图并在 JSON 中引用。
