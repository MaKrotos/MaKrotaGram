plugins {
    id("com.android.library")
}

android {
    namespace = "com.fdw.sugar_pocketai"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Room
    val roomVersion = "2.8.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")

    // WorkManager (Java)
    implementation("androidx.work:work-runtime:2.10.5")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Lifecycle (for LiveData)
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.0")

    // JSON processing
    implementation("org.json:json:20240303")

    // LiteRT LM for on-device LLM inference
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.9.0-beta")


}