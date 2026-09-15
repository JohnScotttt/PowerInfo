import java.net.HttpURLConnection
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 构建时拉取一次开发者头像到 assets，保证每次编译的头像都是最新的；
// 运行时直接读本地 assets，无需实时访问 GitHub，避免头像“慢半拍”加载。
//
// 头像与用户名的对应关系维护在这里；用户名需与 AboutContent.DEVELOPERS 中的 name 一致。
val developerAvatars = mapOf(
    "JohnScotttt" to "https://github.com/JohnScotttt.png",
    // 酷安用户头像（直链由 uid 推导，JPEG 内容存为 .png 文件名不影响解码）。
    "花橋桥" to "http://avatar.coolapk.com/data/030/19/14/24_avatar_big.jpg",
)

val avatarsDir = layout.projectDirectory.dir("src/main/assets/avatars")

val fetchDeveloperAvatars by tasks.registering {
    description = "构建前拉取开发者头像到 assets/avatars，保证每次编译头像最新"
    // 在配置阶段捕获为局部变量，避免 task action 引用脚本对象（configuration cache 不支持序列化脚本引用）。
    val avatars = developerAvatars
    val outDir = avatarsDir.asFile
    // 用户名列表变化时任务重新执行。
    inputs.property("avatars", avatars.keys.sorted().joinToString(","))
    outputs.dir(avatarsDir)

    doLast {
        outDir.mkdirs()
        // 清掉旧头像，避免残留已移除的开发者。
        outDir.listFiles()?.forEach { it.delete() }

        for ((name, url) in avatars) {
            val target = outDir.resolve("$name.png")
            try {
                val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true // github.com/<user>.png 会 302 到 CDN
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", "PowerInfo-build")
                }
                conn.inputStream.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                logger.lifecycle("fetched avatar: $name -> ${target.name}")
            } catch (e: Exception) {
                // 下载失败不阻断构建；运行时会回退到网络 URL。
                logger.warn("failed to fetch avatar for $name from $url: ${e.message}")
                target.delete()
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(fetchDeveloperAvatars)
}

android {
    namespace = "com.ruaorz.powerinfo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ruaorz.powerinfo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}