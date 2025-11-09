# TabBar 图标配置说明

## 当前状态

目前TabBar配置为纯文字模式，不包含图标。如果需要添加图标，请按照以下步骤操作：

## 添加图标步骤

### 1. 创建图标文件

在 `miniprogram` 目录下创建 `images` 文件夹，并准备以下图标文件：

- `chat.png` - 聊天图标（未选中状态）
- `chat_active.png` - 聊天图标（选中状态）
- `history.png` - 历史图标（未选中状态）
- `history_active.png` - 历史图标（选中状态）
- `settings.png` - 设置图标（未选中状态）
- `settings_active.png` - 设置图标（选中状态）

### 2. 图标规格要求

- **尺寸**: 建议 81px × 81px（实际显示时会被缩放）
- **格式**: PNG格式，支持透明背景
- **颜色**: 
  - 未选中状态：建议使用灰色（#7A7E83）
  - 选中状态：建议使用绿色（#07c160）或其他主题色

### 3. 修改 app.json

在 `app.json` 的 `tabBar` 配置中添加图标路径：

```json
"tabBar": {
  "color": "#7A7E83",
  "selectedColor": "#07c160",
  "borderStyle": "black",
  "backgroundColor": "#ffffff",
  "list": [
    {
      "pagePath": "pages/index/index",
      "iconPath": "images/chat.png",
      "selectedIconPath": "images/chat_active.png",
      "text": "聊天"
    },
    {
      "pagePath": "pages/history/history",
      "iconPath": "images/history.png",
      "selectedIconPath": "images/history_active.png",
      "text": "历史"
    },
    {
      "pagePath": "pages/settings/settings",
      "iconPath": "images/settings.png",
      "selectedIconPath": "images/settings_active.png",
      "text": "设置"
    }
  ]
}
```

### 4. 图标资源推荐

可以使用以下方式获取图标：

1. **IconFont（推荐）**
   - 访问 https://www.iconfont.cn/
   - 搜索"聊天"、"历史"、"设置"等关键词
   - 下载PNG格式图标

2. **免费图标库**
   - Flaticon: https://www.flaticon.com/
   - Icons8: https://icons8.com/
   - Material Icons: https://fonts.google.com/icons

3. **设计工具**
   - 使用 Figma、Sketch 等工具自行设计
   - 使用在线图标生成器

## 临时方案（当前）

如果暂时不需要图标，可以保持当前的纯文字TabBar配置。这样小程序可以正常编译和运行，只是没有图标显示。

## 注意事项

1. 图标文件路径必须是相对于小程序根目录的路径
2. 图标文件大小建议控制在 40KB 以内
3. 图标应该简洁明了，易于识别
4. 选中和未选中状态的图标应该有明显的视觉区别

