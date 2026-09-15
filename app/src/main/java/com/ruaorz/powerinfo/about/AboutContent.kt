package com.ruaorz.powerinfo.about

/**
 * 关于页展示用的开发者/贡献者信息。
 *
 * @param name 名称。
 * @param role 角色描述。
 * @param avatarUrl 头像图片地址；为空时回退为默认图标。
 * @param profileUrl 点击整行跳转的主页地址；为空时该行不可点击。
 */
data class Developer(
    val name: String,
    val role: String,
    val avatarUrl: String? = null,
    val profileUrl: String? = null,
)

/** 关于页占位内容。链接与人员信息待替换为真实值。 */
object AboutContent {

    /** GitHub 仓库地址。 */
    const val GITHUB_URL = "https://github.com/JohnScotttt/PowerInfo"

    /** 开发者/贡献者列表。 */
    val DEVELOPERS = listOf(
        Developer(
            name = "JohnScotttt",
            role = "PowerInfo Developer",
            avatarUrl = "https://github.com/JohnScotttt.png",
            profileUrl = "https://github.com/JohnScotttt",
        ),
        Developer(
            name = "花橋桥",
            role = "Contributor",
            // 酷安头像直链：路径由 uid 30191424 补零 8 位后每两位分段推导得到。
            avatarUrl = "http://avatar.coolapk.com/data/030/19/14/24_avatar_big.jpg",
            profileUrl = "https://www.coolapk.com/u/30191424",
        ),
    )
}
