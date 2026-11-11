import java.net.URI
import java.net.URISyntaxException
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// 서버 주소. 비어 있으면 Fake API로 동작(시연·화면 개발용).
// local.properties에 `blinker.serverUrl=http://10.0.2.2:8080` (에뮬레이터 → 내 PC) 또는 -Pblinker.serverUrl=…
val serverUrl: String = (project.findProperty("blinker.serverUrl") as String?)
    ?: localProps.getProperty("blinker.serverUrl")
    ?: ""

// 형식이 틀린 주소는 빌드를 실패시킴 (앱에서 연결이 안 되는 것을 실행 전에 알 수 있게)
val serverUri: URI? = serverUrl.takeIf { it.isNotBlank() }?.let { url ->
    val uri = try {
        URI(url)
    } catch (e: URISyntaxException) {
        null
    }
    val scheme = uri?.scheme
    val validScheme = scheme != null && (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true))
    if (uri == null || !validScheme || uri.host.isNullOrBlank()) {
        throw GradleException("blinker.serverUrl은 http(s)://호스트[:포트] 형식이어야 합니다: $serverUrl")
    }
    uri
}

android {
    namespace = "com.example.blinkerleague"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.blinkerleague"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
    }

    // 출시용 서명: 키는 저장소에 넣지 않고 local.properties(blinker.release.*)에서 읽음. 없으면 서명 없이 빌드
    val releaseProps = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    val releaseStoreFile = releaseProps.getProperty("blinker.release.storeFile")
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseProps.getProperty("blinker.release.storePassword")
                keyAlias = releaseProps.getProperty("blinker.release.keyAlias")
                keyPassword = releaseProps.getProperty("blinker.release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // CarPropertyManager(android.car) 사용. Android Developers의 Automotive OS 가이드 방식
    useLibrary("android.car")

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        // Kotlin 1.9.22와 짝이 맞는 Compose Compiler 버전
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    testOptions {
        // Robolectric으로 Room DAO를 JVM 단위 테스트에서 실행
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // 음성 안내. 에뮬레이터 caas 4.2.10 ≥ SDK 최소 요구 4.1.29
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    // 내비 정보(TBT, 현재 위치 등). 지금은 조사 코드(src/debug)에서만 써서 디버그 빌드에만 넣음.
    // 에뮬레이터 navi 서비스 3.0.4 ≥ SDK 최소 요구 2.0.1
    debugImplementation("ai.pleos.playground:NaviHelper:3.0.5")
    // 차량 위치 (문서 기준 버전). 실차 지원 여부는 확인 필요 (구현 가이드에는 미지원으로 적혀 있음).
    // 에뮬레이터 fused location 서비스 1.0.0 ≥ SDK 최소 요구 0.5.0
    implementation("ai.pleos.playground:FusedLocation:1.1.3")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Lifecycle (MVVM)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room (운행·이벤트 선저장, 업로드 대기열)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // 업로드 재전송 (네트워크 연결 시 실행, 지수 백오프)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // 서버 API: Retrofit + OkHttp, JSON은 kotlinx.serialization 변환기
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // JSON (이벤트 payload, API DTO). HTTP 클라이언트(Retrofit/Ktor)와 무관하게 씀
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}