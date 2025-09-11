package com.example.wooauto.utils

import java.util.Locale

/**
 * URL 规范化工具
 * - 去除首尾空格/多余空白
 * - 若缺少协议则自动补全为 https://
 * - 去除末尾多余斜杠
 * - 移除已附带的 REST 路径（/wp-json/...）、/index.php、以及 ?rest_route= 形式
 * - 仅返回“站点根 URL”（不包含 wp-json 路径）
 */
object UrlNormalizer {
    fun sanitizeSiteUrl(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) return ""

        // 去掉所有空白字符
        s = s.replace("\\s+".toRegex(), "")

        // 缺少协议时，默认补 https
        val lower = s.lowercase(Locale.ROOT)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            s = "https://$s"
        }

        // 移除 REST 路径片段或 index.php、?rest_route=
        s = s.replace("/wp-json(?:/.*)?$".toRegex(RegexOption.IGNORE_CASE), "")
        s = s.replace("/index\\.php$".toRegex(RegexOption.IGNORE_CASE), "")
        s = s.replace("\\?rest_route=.*$".toRegex(RegexOption.IGNORE_CASE), "")

        // 去掉结尾斜杠
        s = s.trimEnd('/')
        return s
    }

    /**
     * 基于站点 URL 构建 WooCommerce v3 API 根路径，确保返回以 / 结尾
     */
    fun buildApiBaseUrl(siteUrl: String): String {
        val base = sanitizeSiteUrl(siteUrl)
        if (base.isEmpty()) return ""
        return "$base/wp-json/wc/v3/"
    }
}


