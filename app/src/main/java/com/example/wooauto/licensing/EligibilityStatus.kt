package com.example.wooauto.licensing

/**
 * 统一的用户资格状态
 * 合并了证书状态和试用期状态，简化UI权限判断
 * 
 * 默认策略：允许使用，验证失败后才锁定
 */
enum class EligibilityStatus {
    /** 有资格使用 - 证书有效或试用期有效（默认状态） */
    ELIGIBLE,
    
    /** 无资格使用 - 证书无效且试用期过期 */
    INELIGIBLE,
    
    /** 正在检查中 - 验证状态中，但仍允许使用 */
    CHECKING,
    
    /** 未知状态 - 但默认允许使用 */
    UNKNOWN
}

/**
 * 详细的资格信息
 * 包含资格状态和相关详情
 */
data class EligibilityInfo(
    val status: EligibilityStatus = EligibilityStatus.ELIGIBLE,
    val isLicensed: Boolean = false,
    val isTrialActive: Boolean = true,
    val trialDaysRemaining: Int = 10,
    val licenseEndDate: String = "",
    val displayMessage: String = "默认试用期有效",
    val source: EligibilitySource = EligibilitySource.TRIAL
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