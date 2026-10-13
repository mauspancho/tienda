plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}
val lanHost = providers.gradleProperty("lanHost").orNull
if (lanHost != null) {
    val octets = lanHost.split('.').map { it.toIntOrNull() ?: -1 }
    require(octets.size == 4 && octets.all { it in 0..255 } &&
        (octets[0] == 10 || (octets[0] == 192 && octets[1] == 168) ||
        (octets[0] == 172 && octets[1] in 16..31))) { "lanHost debe ser una IPv4 privada" }
}
val generateNetworkConfig = tasks.register("generateNetworkConfig") {
    val output = layout.buildDirectory.dir("generated/networkRes")
    inputs.property("lanHost", lanHost ?: "")
    outputs.dir(output)
    doLast {
        val file = output.get().file("xml/network_security_config.xml").asFile
        file.parentFile.mkdirs()
        val domain = lanHost?.let { "<domain-config cleartextTrafficPermitted=\"true\"><domain includeSubdomains=\"false\">$it</domain></domain-config>" } ?: ""
        file.writeText("<network-security-config><base-config cleartextTrafficPermitted=\"false\"><trust-anchors><certificates src=\"system\" /></trust-anchors></base-config>$domain</network-security-config>")
    }
}
android {
    namespace = "com.mauspancho.tiendapos"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.mauspancho.tiendapos"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "LAN_HOST", "\"${lanHost ?: ""}\"")
    }
    sourceSets["main"].res.srcDir(layout.buildDirectory.dir("generated/networkRes").get().asFile)
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
tasks.named("preBuild").configure { dependsOn(generateNetworkConfig) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.paging:paging-compose:3.3.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("androidx.camera:camera-camera2:1.5.0")
    implementation("androidx.camera:camera-lifecycle:1.5.0")
    implementation("androidx.camera:camera-view:1.5.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
