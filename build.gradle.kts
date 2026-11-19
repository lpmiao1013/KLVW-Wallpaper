// Hilt is on the buildscript classpath (not plugins DSL) so CI resolves the real
// Maven artifact com.google.dagger:hilt-android-gradle-plugin instead of the
// flaky plugin-marker POM under pluginManagement.
buildscript {
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
    dependencies {
        classpath(libs.hilt.android.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
