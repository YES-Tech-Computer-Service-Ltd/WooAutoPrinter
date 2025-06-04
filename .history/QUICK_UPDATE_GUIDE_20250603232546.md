# WooAuto 快速更新指南 ⚡

## 🎯 一分钟更新流程

### 当前版本: `0.2.1` → 目标版本: `0.2.2`

```bash
# 1. 更新版本号
./gradlew createNewVersion -PnewVersion=patch

# 2. 构建APK
./gradlew clean && ./gradlew assembleRelease
```

**APK位置**: `app/build/outputs/apk/release/app-release.apk`

### 🌐 YesTech网站操作

1. **登录**: https://yestech.com.hk/wp-admin
2. **导航**: 设置 → App更新
3. **更新版本**: 输入 `0.2.2` → 点击"更新版本"
4. **上传APK**: 选择文件 → 上传APK
5. **测试**: 点击"测试检查更新API"

## 📋 版本类型快速选择

| 更新类型 | 命令 | 示例变更 |
|---------|-----|---------|
| 🐛 Bug修复 | `./gradlew createNewVersion -PnewVersion=patch` | 0.2.1 → 0.2.2 |
| ✨ 新功能 | `./gradlew createNewVersion -PnewVersion=minor` | 0.2.1 → 0.3.0 |
| 💥 重大更改 | `./gradlew createNewVersion -PnewVersion=major` | 0.2.1 → 1.0.0 |

## 🔧 实用命令

```bash
# 设置为测试版
./gradlew setBetaState -PisBeta=true

# 设置为正式版  
./gradlew setBetaState -PisBeta=false

# 手动递增构建号
./gradlew updateVersionCode
```

## ✅ 发布检查清单

- [ ] 代码提交并推送
- [ ] 功能测试完成
- [ ] 版本号已更新
- [ ] APK构建成功
- [ ] 上传到YesTech服务器
- [ ] API测试通过
- [ ] 客户端更新测试

## 🚨 紧急修复

```bash
# 立即修复 + 发布
./gradlew createNewVersion -PnewVersion=patch
./gradlew clean assembleRelease
# 然后立即上传到服务器
```

## 📱 版本号规则

**格式**: `major.minor.patch[-beta]`
- **major**: 不兼容变更
- **minor**: 新功能，向下兼容  
- **patch**: Bug修复，向下兼容
- **beta**: 测试版标识

## 🔗 常用链接

- **WordPress后台**: https://yestech.com.hk/wp-admin
- **更新管理**: 设置 → App更新
- **API测试**: WordPress管理页面的"测试检查更新API"按钮

---
**快速参考** | 保存此页面以便快速查看更新步骤 