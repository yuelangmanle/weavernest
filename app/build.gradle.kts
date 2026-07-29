import java.util.Properties
import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.kapt")
}

val signingProperties = Properties().apply {
    val propertiesFile = rootProject.file(".local/keys/zhique-release.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use(::load)
}
val hasLocalSigningKey = signingProperties.getProperty("storeFile") != null

android {
    namespace = "com.zhique.studio"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.zhique.studio"
        minSdk = 29
        targetSdk = 35
        versionCode = 7
        versionName = "0.5.0-alpha"
        buildConfigField("String", "GITHUB_REPOSITORY", "\"yuelangmanle/weavernest\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("localRelease") {
            val storePath = signingProperties.getProperty("storeFile")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (hasLocalSigningKey) signingConfig = signingConfigs.getByName("localRelease")
        }
        getByName("release") {
            signingConfig = if (hasLocalSigningKey) signingConfigs.getByName("localRelease") else signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/templateRuntimeAssets"))
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":runtime"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.webkit)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.zip4j)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.security.crypto)
    implementation(libs.arsc.lib)
    implementation(libs.apk.sig)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(kotlin("test"))
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

kapt {
    arguments {
        arg("room.schemaLocation", file("schemas").path)
    }
}

val generatedTemplateAssets = layout.buildDirectory.dir("generated/templateRuntimeAssets")
val copyTemplateRuntimeApk by tasks.registering(Copy::class) {
    dependsOn(":template-runtime:assembleRelease")
    from(project(":template-runtime").layout.buildDirectory.file("outputs/apk/release/template-runtime-release-unsigned.apk"))
    into(generatedTemplateAssets.map { it.dir("template") })
    rename { "zhique-template-runtime.apk" }
}

tasks.matching { task -> task.name.startsWith("merge") && task.name.endsWith("Assets") }
    .configureEach { dependsOn(copyTemplateRuntimeApk) }
