# Git 推送到 Gitee 指南

## 已完成的工作

1. ✅ Git 已自动安装
2. ✅ Git 已配置 UTF-8 编码
3. ✅ Git 仓库已初始化
4. ✅ 所有文件已提交到本地仓库
5. ⚠️ 需要身份验证才能推送到 Gitee

## 推送步骤

### 方法一：使用访问令牌（推荐）

1. **在 Gitee 创建访问令牌**
   - 访问：https://gitee.com/profile/personal_access_tokens
   - 点击"生成新令牌"
   - 设置令牌描述，勾选 `projects` 权限
   - 点击"提交"，复制生成的令牌

2. **使用令牌推送**
   ```powershell
   cd C:\Users\Administrator\Desktop\NEXUS-main
   git push -u origin main
   ```
   - 用户名输入：`zhk567`
   - 密码输入：粘贴刚才复制的访问令牌

### 方法二：使用用户名密码

```powershell
cd C:\Users\Administrator\Desktop\NEXUS-main
git push -u origin main
```
- 用户名：`zhk567`
- 密码：您的 Gitee 账户密码

### 方法三：在 URL 中包含用户名

```powershell
cd C:\Users\Administrator\Desktop\NEXUS-main
git remote set-url origin https://zhk567@gitee.com/zhk567/NEXUS.git
git push -u origin main
```
然后输入密码或访问令牌。

## 注意事项

- Git 已配置为使用 UTF-8 编码，支持中文文件名和提交信息
- 如果推送失败，请检查：
  1. Gitee 仓库是否存在
  2. 您是否有推送权限
  3. 网络连接是否正常

## 验证推送

推送成功后，访问 https://gitee.com/zhk567/NEXUS 查看代码。

