import java.time.ZonedDateTime

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.sel2in.suzysnooze"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sel2in.suzysnooze"
        minSdk = 29
        targetSdk = 35

        val versionFile = rootProject.file("version.txt")
        val versionNameValue = if (versionFile.exists()) {
            versionFile.readText().trim().ifEmpty { "1.0.0" }
        } else {
            "1.0.0"
        }
        versionName = versionNameValue

        val versionParts = versionNameValue.split('.')
        val major = versionParts.getOrElse(0) { "0" }.toIntOrNull() ?: 0
        val minor = versionParts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
        val patch = versionParts.getOrElse(2) { "0" }.toIntOrNull() ?: 0
        versionCode = major * 10000 + minor * 100 + patch

        val buildDateFile = rootProject.file("buildDate.txt")
        val buildDateValue = if (buildDateFile.exists()) {
            buildDateFile.readText().trim().ifEmpty { "Unknown" }
        } else {
            ZonedDateTime.now().toString()
        }

        buildConfigField("String", "BUILD_VERSION", "\"$versionNameValue\"")
        buildConfigField("String", "BUILD_DATE", "\"$buildDateValue\"")

        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("REACHME_KEYSTORE_PATH")
                ?: "${rootProject.projectDir}/../private/s2n/keys/upload-key.jks"
            val keystorePassword = System.getenv("REACHME_KEYSTORE_PASS")
                ?: System.getenv("S2n_Jks")
                ?: ""
            val keyAliasName = System.getenv("REACHME_KEY_ALIAS") ?: "sel2in_upload"
            val keyPass = System.getenv("REACHME_KEY_PASS") ?: keystorePassword

            val keystoreFile = file(keystorePath)
            if (keystoreFile.exists() && keystorePassword.isNotEmpty()) {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPass
            } else {
                println("⚠️ Release keystore not configured - using default debug signing")
                println("   Set REACHME_KEYSTORE_PATH and REACHME_KEYSTORE_PASS to sign release builds")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = project.hasProperty("minifyEnabled") &&
                project.property("minifyEnabled").toString().toBoolean()
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    lint {
        // Disable lintVital on release builds (it needs network for dependencies).
        // Use ./build.sh l1 or l2 to run lint explicitly when needed.
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val buildTypeName = buildType.name
            val extension = output.outputFile.extension.ifEmpty { "apk" }
            output.outputFileName = "sel2in_snooze_${buildTypeName}.$extension"
        }
    }
}

tasks.whenTaskAdded {
    if (name.startsWith("bundle")) {
        doLast {
            val buildType = name.removePrefix("bundle").replaceFirstChar { it.lowercase() }
            val bundleDir = file("${layout.buildDirectory.get()}/outputs/bundle/${buildType}")
            if (bundleDir.exists()) {
                val defaultAab = bundleDir.resolve("app-${buildType}.aab")
                val renamed = bundleDir.resolve("sel2in_snooze_${buildType}.aab")
                if (defaultAab.exists()) {
                    defaultAab.renameTo(renamed)
                    println("✅ Renamed AAB to: ${renamed.name}")
                }
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
