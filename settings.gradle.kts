pluginManagement {
    repositories {
        // 阿里云镜像仅用于本地（国内网络）加速；GitHub Actions 等海外 CI 环境
        // 访问阿里云可能被限流返回异常（403/错误页），Gradle 会因此直接终止插件
        // 解析、不回落官方源，导致构建失败。CI 上走官方仓库。
        if (System.getenv("CI").isNullOrEmpty()) {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 同上：阿里云镜像仅本地使用，CI 走官方源
        if (System.getenv("CI").isNullOrEmpty()) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "KgCheckin"
include(":app")
