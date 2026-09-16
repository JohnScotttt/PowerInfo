import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// release 签名信息放在项目根目录的 keystore.properties（已在 .gitignore 忽略，不入库）。
// 缺失时保持空配置，本地或 CI 无密钥也能正常构建（release 退回未签名产物）。
// 格式见 keystore.properties.example。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
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

        // 记录下载失败/产物缺失的开发者，收集完后统一在末尾中断构建，
        // 避免带着缺失头像的包被静默打出来。
        val failed = mutableListOf<String>()

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
                // 校验产物确实写入且非空，空文件同样视为失败。
                if (!target.exists() || target.length() == 0L) {
                    target.delete()
                    failed += "$name (产物为空: $url)"
                } else {
                    logger.lifecycle("fetched avatar: $name -> ${target.name} (${target.length()} bytes)")
                }
            } catch (e: Exception) {
                target.delete()
                failed += "$name ($url): ${e.message}"
            }
        }

        // 任意头像缺失即中断构建，逼出问题（网络/代理/URL 失效），而非发布缺图的包。
        if (failed.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("开发者头像下载失败，已中断构建。请检查网络/代理或头像 URL 后重试：")
                    failed.forEach { appendLine("  - $it") }
                }.trimEnd()
            )
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
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // keystore.properties 缺失时保持空配置，避免无密钥环境构建直接失败。
            if (keystoreProperties.isNotEmpty()) {
                storeFile = keystoreProperties.getProperty("storeFile")?.let { rootProject.file(it) }
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
            // 仅启用 APK Signature Scheme v2 + v3；minSdk=26 无需 v1(JAR) 签名，v4 按需另开。
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = false
        }
    }

    buildTypes {
        release {
            // 有 keystore 才启用 release 签名，否则保持未签名产物、不阻断构建。
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
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

// 归档命名后的 APK，保留每个版本的历史产物（AGP 9 新 DSL，legacy applicationVariants 已不可用）：
//   release -> apk-archive/PowerInfo-v<版本>.apk
//   debug   -> apk-archive/PowerInfo-v<版本>+<第几次build>-debug.apk
// 归档目录 app/apk-archive 在 build 外（gitignore，AGP 不清、clean 不动），历史全留、同版本多份共存；
// outputs/apk 里保持 AGP 原样（app-<type>.apk）供 IDE 安装运行。
//
// debug 的 build 次数按“同一版本号”分别计数，并跨 ./gradlew clean 持久递增：
// 计数存在 build 目录外的 .build-numbers.properties（gitignore，clean 不清），
// 格式 <versionName>=<已用的最大 N>。每次 debug 打包读取该版本的值 +1 作为本次序号并写回。
//
// 做法：钩住 AGP 的打包任务 package<Variant>，在其 doLast 里把产出的 app-*.apk 复制进归档目录。
val buildNumbersFile = layout.projectDirectory.file(".build-numbers.properties").asFile
val apkArchiveDir = layout.projectDirectory.dir("apk-archive").asFile

extensions.configure<ApplicationAndroidComponentsExtension>("androidComponents") {
    onVariants { variant ->
        val version = variant.outputs.first().versionName.map { it ?: "0" }
        val isDebug = variant.buildType == "debug"
        val packageTaskName = "package${variant.name.replaceFirstChar { it.uppercase() }}"

        // package<Variant> 任务此刻可能尚未注册，用 configureEach 按名延迟匹配，命中后再挂 doLast。
        tasks.configureEach {
            if (name != packageTaskName) return@configureEach
            // 在配置阶段捕获局部变量，避免 doLast 引用脚本对象（configuration cache 友好）。
            val versionProvider = version
            val outDir = layout.buildDirectory
                .dir("outputs/apk/${variant.name}").get().asFile
            val countersFile = buildNumbersFile
            val archiveDir = apkArchiveDir
            doLast {
                // outputs/apk/<variantName>/ 下 AGP 产出的 app-*.apk。
                val src = outDir.listFiles()
                    ?.firstOrNull { it.extension == "apk" && it.name.startsWith("app-") }
                    ?: return@doLast

                val v = versionProvider.get()
                val newName = if (isDebug) {
                    // 从持久计数文件读该版本已用的最大 N，+1 作为本次序号并写回。
                    val props = Properties()
                    if (countersFile.exists()) {
                        countersFile.inputStream().use { props.load(it) }
                    }
                    val next = (props.getProperty(v)?.toIntOrNull() ?: 0) + 1
                    props.setProperty(v, next.toString())
                    countersFile.outputStream().use {
                        props.store(it, "PowerInfo debug build numbers per versionName")
                    }
                    "PowerInfo-v$v+$next-debug.apk"
                } else {
                    "PowerInfo-v$v.apk"
                }

                // 复制进归档目录（源留在 outputs/apk 供 IDE 安装）。debug 靠 +N 天然不重名、历史全留；
                // release 同版本重复 build 会覆盖同名文件，符合“每个版本一份”的预期。
                archiveDir.mkdirs()
                src.copyTo(archiveDir.resolve(newName), overwrite = true)
                logger.lifecycle("archived apk: apk-archive/$newName")
            }
        }
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
    // miuix：可选的 UI 风格（关于页里可切换 miuix / material），核心 + preference（含 SuperDropdown）。
    implementation(libs.miuix.core)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}