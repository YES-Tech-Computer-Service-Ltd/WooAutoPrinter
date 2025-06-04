# WooAuto 应用更新指南

## 📋 更新前准备清单

### 1. 确认更新内容
- [ ] 功能更新清单
- [ ] Bug修复列表
- [ ] 更新日志准备
- [ ] 测试完成确认

### 2. 版本号管理
WooAuto使用语义化版本号 (Semantic Versioning)：`major.minor.patch`

- **Major (主版本号)**: 不兼容的API修改
- **Minor (次版本号)**: 向下兼容的功能性新增
- **Patch (补丁版本号)**: 向下兼容的问题修正

**当前版本**: `0.2.1` (版本代号: 8)

## 🔧 步骤一：更新版本号

### 自动更新版本号（推荐）

```bash
# 补丁版本更新 (0.2.1 → 0.2.2)
./gradlew createNewVersion -PnewVersion=patch

# 次版本更新 (0.2.1 → 0.3.0)
./gradlew createNewVersion -PnewVersion=minor

# 主版本更新 (0.2.1 → 1.0.0)
./gradlew createNewVersion -PnewVersion=major
```

### 手动更新版本号

编辑 `version.properties` 文件：

```properties
# 应用版本信息
major=0
minor=2
patch=2  # 更新补丁版本号
build=1  # 重置构建号
versionCode=9  # 递增版本代号
versionName=0.2.2  # 更新版本名称
isBeta=false  # 发布版本设为false
```

### 版本号命名规范

- **开发版本**: `0.x.x-beta`
- **候选版本**: `0.x.x-rc1`
- **正式版本**: `0.x.x`

```bash
# 设置为测试版
./gradlew setBetaState -PisBeta=true

# 设置为正式版
./gradlew setBetaState -PisBeta=false
```

## 🏗️ 步骤二：构建APK

### 1. 清理项目
```bash
./gradlew clean
```

### 2. 构建Release版本
```bash
./gradlew assembleRelease
```

### 3. 验证构建结果
- APK位置: `app/build/outputs/apk/release/app-release.apk`
- 检查APK文件大小是否合理
- 验证APK签名正确

### 4. 测试APK
- [ ] 安装测试
- [ ] 功能验证
- [ ] 性能测试
- [ ] 兼容性测试

## 🌐 步骤三：上传到YesTech网站

### 1. 登录WordPress后台
访问：`https://yestech.com.hk/wp-admin`

### 2. 进入更新管理页面
导航到：`设置` → `App更新`

### 3. 更新版本号
在 **应用信息** 部分：
- 在 "当前版本号" 字段输入新版本号（如：`0.2.2`）
- 点击 **更新版本** 按钮

### 4. 上传APK文件
在 **APK文件管理** 部分：
- 点击 **选择文件** 按钮
- 选择构建好的 `app-release.apk` 文件
- 点击 **上传APK** 按钮

### 5. 验证上传结果
- [ ] 确认文件上传成功
- [ ] 检查文件大小显示正确
- [ ] 版本号显示正确

## 🧪 步骤四：测试更新功能

### 1. 测试API接口
在WordPress管理页面点击 **测试检查更新API** 按钮

预期返回格式：
```json
{
    "success": true,
    "current_version": "0.2.2",
    "has_update": true,
    "download_url": "https://yestech.com.hk/wp-json/app-updater/v1/download?token=xxxxx",
    "file_size": 15728640
}
```

### 2. 客户端测试
1. 在旧版本应用中触发更新检查
2. 验证更新提示显示
3. 测试下载功能
4. 测试安装更新

## 📱 步骤五：客户端更新机制

### 自动检查更新
应用每次启动时会自动检查更新，检查间隔可配置。

### 手动检查更新
用户可以在设置页面手动触发更新检查。

### 更新流程
1. **检查阶段**: 比较本地版本与服务器版本
2. **下载阶段**: 下载新APK到本地存储
3. **安装阶段**: 调用系统安装程序

## 🔐 安全配置

### API Token管理
- 每个API请求都需要验证Token
- Token可以在WordPress后台重新生成
- 确保Token安全性，不要泄露

### 文件安全
- APK文件存储在受保护目录
- 直接访问APK文件将被拒绝
- 只能通过API接口下载

## 📝 版本发布清单

### 发布前检查
- [ ] 代码审查完成
- [ ] 单元测试通过
- [ ] 集成测试通过
- [ ] 性能测试完成
- [ ] 安全检查完成
- [ ] 更新日志准备就绪

### 发布步骤
- [ ] 更新版本号
- [ ] 构建Release APK
- [ ] 上传到YesTech服务器
- [ ] 测试更新功能
- [ ] 通知用户更新

### 发布后监控
- [ ] 监控下载统计
- [ ] 收集用户反馈
- [ ] 监控崩溃报告
- [ ] 准备热修复方案

## 🚨 紧急修复流程

如果发现严重问题需要紧急修复：

1. **立即修复**: 创建hotfix分支
2. **快速测试**: 进行必要的测试
3. **紧急发布**: 
   ```bash
   # 紧急补丁版本
   ./gradlew createNewVersion -PnewVersion=patch
   ./gradlew assembleRelease
   ```
4. **推送更新**: 立即上传到服务器
5. **通知用户**: 发送紧急更新通知

## 📊 版本管理工具

### Gradle任务
```bash
# 查看当前版本信息
./gradlew -q printVersion

# 更新构建号（每次构建自动执行）
./gradlew updateVersionCode

# 创建新版本
./gradlew createNewVersion -PnewVersion=[major|minor|patch]

# 设置Beta状态
./gradlew setBetaState -PisBeta=[true|false]
```

### 版本历史追踪
在 `version.properties` 文件中记录版本变更历史。

## 🔧 故障排除

### 常见问题

**Q: 上传APK失败**
- A: 检查文件大小限制，确保APK文件完整

**Q: API测试失败**
- A: 检查Token是否正确，网络连接是否正常

**Q: 客户端检测不到更新**
- A: 确认版本号格式正确，服务器版本号大于客户端版本号

**Q: 下载更新失败**
- A: 检查存储权限，确保下载目录可写

### 日志查看
- WordPress错误日志：`wp-content/debug.log`
- 应用日志：Android Studio Logcat

## 📞 技术支持

如遇到技术问题，请联系：
- 开发团队：technical@yestech.com.hk
- 紧急联系：+852-XXXX-XXXX

---

**最后更新**: 2024年12月
**文档版本**: 1.0.0 