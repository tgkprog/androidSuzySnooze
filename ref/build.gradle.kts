import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.sel2in.reachme"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sel2in.reachme"
        minSdk = 31  // Android 12
        targetSdk = 35  // Android 15
        
        // Read version code from versionCode.txt (auto-incremented by build.sh)
        val versionCodeFile = file("${rootProject.projectDir}/versionCode.txt")
        versionCode = if (versionCodeFile.exists()) {
            versionCodeFile.readText().trim().toIntOrNull() ?: 1
        } else {
            1
        }
        
        // Read version from version.txt (manually updated)
        val versionFile = file("${rootProject.projectDir}/version.txt")
        versionName = if (versionFile.exists()) {
            versionFile.readText().trim()
        } else {
            "0.0.001"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Read build date from buildDate.txt (auto-updated by build.sh)
        val buildDateFile = file("${rootProject.projectDir}/buildDate.txt")
        val buildDate = if (buildDateFile.exists()) {
            buildDateFile.readText().trim()
        } else {
            // Fallback: compute live if file doesn't exist
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX")
            dateFormat.timeZone = TimeZone.getDefault()
            dateFormat.format(Date())
        }
        
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
        buildConfigField("long", "BUILD_NUMBER", "${System.currentTimeMillis() / 1000}")
        
        // Server URL from -PserverUrl or default
        val serverUrl = if (project.hasProperty("serverUrl")) {
            project.property("serverUrl") as String
        } else {
            "https://rq4.sel2in.com" // Default to rq4 if not provided
        }
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        
        // Logging toggles (from .env or defaults)
        val logApiCheck = project.findProperty("LOG_API_CHECK")?.toString()?.toBoolean() ?: false
        val logReachMeRecd = project.findProperty("LOG_REACHME_RECD")?.toString()?.toBoolean() ?: false
        buildConfigField("boolean", "LOG_API_CHECK_DEFAULT", "$logApiCheck")
        buildConfigField("boolean", "LOG_REACHME_RECD_DEFAULT", "$logReachMeRecd")
    }

    signingConfigs {
        // Release signing - fully configurable via environment variables
        // Each developer can set their own keystore without sharing production keys
        //
        // Environment Variables:
        //   REACHME_KEYSTORE_PATH   - Path to keystore file (default: ../private/s2n/keys/upload-key.jks)
        //   REACHME_KEYSTORE_PASS   - Keystore password (falls back to S2n_Jks for backward compat)
        //   REACHME_KEY_ALIAS       - Key alias (default: sel2in_upload)
        //   REACHME_KEY_PASS        - Key password (defaults to keystore password)
        //
        // For local dev, create ~/.reachme_signing.env:
        //   export REACHME_KEYSTORE_PATH=/path/to/your/keystore.jks
        //   export REACHME_KEYSTORE_PASS=your_password
        //   export REACHME_KEY_ALIAS=your_alias
        //
        create("release") {
            val keystorePath = project.findProperty("reachmeKeystorePath") as? String
                ?: System.getenv("REACHME_KEYSTORE_PATH") 
                ?: "${rootProject.projectDir}/../private/s2n/keys/upload-key.jks"
            
            val keystorePassword = project.findProperty("reachmeKeystorePass") as? String
                ?: System.getenv("REACHME_KEYSTORE_PASS") 
                ?: System.getenv("S2n_Jks") 
                ?: ""
            
            val keyAliasName = project.findProperty("reachmeKeyAlias") as? String
                ?: System.getenv("REACHME_KEY_ALIAS") 
                ?: "sel2in_upload"
            
            val keyPass = project.findProperty("reachmeKeyPass") as? String
                ?: System.getenv("REACHME_KEY_PASS") 
                ?: keystorePassword
            
            val keystoreFile = file(keystorePath)
            if (keystoreFile.exists() && keystorePassword.isNotEmpty()) {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPass
            } else {
                // Fall back to debug signing if release keystore not available
                // This allows developers without production keys to still build release APKs
                println("⚠️ Release keystore not configured - will use debug signing")
                println("   Set REACHME_KEYSTORE_PATH and REACHME_KEYSTORE_PASS environment variables")
            }
        }
    }

    buildTypes {
        release {
            // Enable R8 minification/obfuscation via: ./gradlew ... -PminifyEnabled=true
            // Mapping file: app/build/outputs/mapping/release/mapping.txt
            isMinifyEnabled = project.hasProperty("minifyEnabled") && 
                              project.property("minifyEnabled").toString().toBoolean()
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    // Rename output APK and AAB files
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val buildTypeName = buildType.name
            output.outputFileName = "reachme_${buildTypeName}.apk"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    ksp("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    // Browser - for CustomTabsIntent (OLT auto-login flow)
    implementation("androidx.browser:browser:1.7.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx") {
        exclude(group = "com.google.android.gms", module = "play-services-ads-identifier")
    }

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// Rename AAB outputs after bundle tasks
tasks.whenTaskAdded {
    if (name.startsWith("bundle")) {
        doLast {
            val buildType = name.removePrefix("bundle").replaceFirstChar { it.lowercase() }
            val aabDir = file("${layout.buildDirectory.get()}/outputs/bundle/${buildType}")
            if (aabDir.exists()) {
                val defaultAab = file("${aabDir}/app-${buildType}.aab")
                val renamedAab = file("${aabDir}/reachme_${buildType}.aab")
                if (defaultAab.exists() && !renamedAab.exists()) {
                    defaultAab.renameTo(renamedAab)
                    println("✅ Renamed AAB to: ${renamedAab.name}")
                }
            }
        }
    }

}
