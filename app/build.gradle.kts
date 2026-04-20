plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt.android)
}

fun readConfig(name: String): String {
    return providers.gradleProperty(name).orNull?.trim()
        ?: System.getenv(name)?.trim()
        ?: ""
}

fun quoted(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

val mqttHost = readConfig("MQTT_HOST")
val mqttPort = readConfig("MQTT_PORT").toIntOrNull() ?: 8883
val mqttScheme = readConfig("MQTT_SCHEME").ifBlank { "tcp" }
val mqttUser = readConfig("MQTT_USER")
val mqttPass = readConfig("MQTT_PASS")

android {
    namespace = "com.example.btl_nhung"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.btl_nhung"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "MQTT_HOST", quoted(mqttHost))
        buildConfigField("int", "MQTT_PORT", mqttPort.toString())
        buildConfigField("String", "MQTT_SCHEME", quoted(mqttScheme))
        buildConfigField("String", "MQTT_USER", quoted(mqttUser))
        buildConfigField("String", "MQTT_PASS", quoted(mqttPass))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.paho.mqtt)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
