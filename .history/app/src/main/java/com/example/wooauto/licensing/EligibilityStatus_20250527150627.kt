package com.example.wooauto.licensing

/**
 * 统一的用户资格状态
 * 合并了证书状态和试用期状态，简化UI权限判断
 */
enum class EligibilityStatus {
    /** 有资格使用 - 证书有效或试用期有效 */
    ELIGIBLE,
    
    /** 无资格使用 - 证书无效且试用期过期 */
    INELIGIBLE,
    
    /** 正在检查中 - 验证状态中 */
    CHECKING,
    
    /** 未知状态 - 初始状态或检查失败 */
    UNKNOWN
}

/**
 * 详细的资格信息
 * 包含资格状态和相关详情
 */
data class EligibilityInfo(
    val status: EligibilityStatus = EligibilityStatus.UNKNOWN,
    val isLicensed: Boolean = false,
    val isTrialActive: Boolean = false,
    val trialDaysRemaining: Int = 0,
    val licenseEndDate: String = "",
    val displayMessage: String = "",
    val source: EligibilitySource = EligibilitySource.UNKNOWN
)

/**
 * 资格来源
 * 表明当前资格是基于什么获得的
 */
enum class EligibilitySource {
    /** 基于有效证书 */
    LICENSE,
    
    /** 基于试用期 */
    TRIAL,
    
    /** 未知来源 */
    UNKNOWN
} 