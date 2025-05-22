/**
 * Deploy Key / PAT Token 生成器
 * 用于生成GitHub认证令牌并添加到local.properties文件中
 *
 * 使用方法:
 * 1. 复制此文件到项目根目录
 * 2. 修改下面的username和token变量
 * 3. 在终端中运行: kotlinc -script deploy_key_generator.kt
 * 
 * 需要kotlinc命令行工具: https://kotlinlang.org/docs/command-line.html
 */

import java.io.File
import java.util.Base64
import java.util.Properties

// ======= 修改以下信息 =======
val username = "你的GitHub用户名" // 替换为你的GitHub用户名
val token = "你的个人访问令牌或Deploy Key" // 替换为你的PAT或Deploy Key
// ===========================

// 生成Base64编码的认证字符串
fun generateAuthToken(username: String, token: String): String {
    val auth = "$username:$token"
    return Base64.getEncoder().encodeToString(auth.toByteArray())
}

// 主函数
fun main() {
    // 生成认证令牌
    val authToken = generateAuthToken(username, token)
    println("生成的认证令牌: $authToken")
    
    // 读取local.properties文件
    val localPropertiesFile = File("local.properties")
    val properties = Properties()
    
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { properties.load(it) }
        println("已读取现有的local.properties文件")
    } else {
        println("local.properties文件不存在，将创建新文件")
    }
    
    // 添加或更新认证令牌
    properties.setProperty("github.auth.token", authToken)
    
    // 保存文件
    localPropertiesFile.outputStream().use { 
        properties.store(it, "添加GitHub认证令牌 - 自动生成，请勿提交此文件") 
    }
    
    println("已将认证令牌保存到local.properties文件中")
    println("警告：请确保local.properties文件已添加到.gitignore中，不要将其提交到版本控制系统中")
}

// 执行主函数
main() 