plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.kgcheckin.app"
    compileSdk = 36

    defaultConfig {
        // applicationId 与项目名对齐；namespace（代码包结构）保持 com.kgcheckin.app 不变
        applicationId = "com.kgautocheckin.app"
        namespace = "com.kgcheckin.app"
        minSdk = 26
        // 兼容 Android 8.0 ~ Android 17（API 35/36/37 目标平台均已适配：
        // edge-to-edge、预测性返回、16KB page size 对齐由纯 Kotlin 实现天然满足）
        targetSdk = 36
        // 1.0.3：应用包名改为 com.kgautocheckin.app（更换应用身份，需卸载旧版重装并重新登录账号）
        // 1.0.4：修复定时闹钟冷启动进程时账号异步加载竞态——杀掉 APP 后到点触发会
        //        误报“暂无已登录账号，跳过本次执行”且不执行签到；现在先等待账号加载完成
        // 1.0.5：架构审计加固（无功能变化）；CI 启用固定签名密钥，此后版本可覆盖安装升级
        // 1.0.6：定时触发跳过场景补静音留痕通知（当日已签过/暂无账号/避让手动执行），
        //        被划掉后台时触发情况在通知栏可见；CI 改为显式固定签名并自动校验
        //        签名指纹（修复 v1.0.5/v1.0.6 首发产物实际使用临时签名的问题）
        // 1.0.7：维护版本——Room 弃用 API 迁移（行为等价写法更新）；CI 依赖缓存提速
        versionCode = 10
        versionName = "1.0.7"
    }

    signingConfigs {
        // CI 固定签名：仅当 workflow 注入 KGAC_KEYSTORE_PATH 环境变量时被 release
        // 构建引用。显式指定密钥文件，不依赖 AGP 对 ~/.android/debug.keystore 的
        // 隐式查找（曾在 CI 上因查找路径与恢复路径不一致而静默改用临时密钥，
        // 导致各版本签名不同、无法覆盖升级）。
        // 安全加固（2026-11-06）：密码类变量一律由 CI Secret 注入，源码零明文回退；
        // 缺失即构建失败（fail-fast），绝不静默回落默认值（与 Grocool 同款标准）。
        create("fixed") {
            System.getenv("KGAC_KEYSTORE_PATH")?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("KGAC_KEYSTORE_STORE_PASS")
                    ?: throw GradleException(
                        "KGAC_KEYSTORE_STORE_PASS 未注入：CI 需配置 Actions Secret KGAC_KEYSTORE_PASS"
                    )
                keyAlias = System.getenv("KGAC_KEYSTORE_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("KGAC_KEYSTORE_KEY_PASS") ?: storePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 个人使用场景：本地构建保持 debug 签名（行为不变）；
            // CI 注入固定密钥路径时改用固定签名，保证各版本签名一致、可覆盖升级
            signingConfig = if (System.getenv("KGAC_KEYSTORE_PATH") != null) {
                signingConfigs.getByName("fixed")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Compose + Material 3（显式版本，对应 compose-bom 2025.05.00）
    implementation("androidx.compose.ui:ui:1.8.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling:1.8.1")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")

    // 持久化：DataStore（轻量偏好）+ Room（签到历史）
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // 网络 + 序列化
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // 二维码生成（ZXing core，纯 Java 实现无 native，体积小）
    implementation("com.google.zxing:core:3.5.3")

    // 酷狗头像网络图加载
    implementation("io.coil-kt:coil-compose:2.7.0")
}