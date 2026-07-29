plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zhique.template"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zhique.template"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures { compose = true }
    sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/zhiqueIconRes"))

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

val stageSuppliedTemplateIcon by tasks.registering(Copy::class) {
    val suppliedIcon = rootProject.file("图标.png")
    onlyIf { suppliedIcon.isFile }
    from(suppliedIcon)
    into(layout.buildDirectory.dir("generated/zhiqueIconRes/drawable"))
    rename { "ic_launcher.png" }
}

tasks.matching { task ->
    (task.name.startsWith("generate") && task.name.endsWith("Resources")) ||
        (task.name.startsWith("map") && task.name.endsWith("SourceSetPaths")) ||
        (task.name.startsWith("merge") && task.name.endsWith("Resources"))
}
    .configureEach { dependsOn(stageSuppliedTemplateIcon) }

dependencies {
    implementation(project(":core"))
    implementation(project(":runtime"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
}
