@file:Suppress("OPT_IN_USAGE_FUTURE_ERROR", "UnstableApiUsage")

plugins {
    id("kmp-application-conventions")
    kotlin("plugin.serialization")
}

android {
    namespace = "io.sellmair.broadheart"

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.3"
//        kotlinCompilerExtensionVersion = "1.4.3-dev-k1.8.20-Beta-15b4f4328eb"
    }
}

kotlin {
    sourceSets.commonMain.get().dependencies {
        implementation(project(":models"))
        implementation(project(":utils"))
        implementation(project(":bluetooth"))
        implementation(project(":shared-ui"))
        implementation(Dependencies.coroutinesCore)
        implementation(Dependencies.okio)
    }

    sourceSets.androidMain.get().dependencies {
        /* androidx */
        implementation(platform("androidx.compose:compose-bom:2022.12.00"))
        implementation("androidx.activity:activity-compose:1.7.0-rc01")
        implementation("androidx.compose.material3:material3")
        implementation("androidx.compose.ui:ui-tooling-preview:1.3.3")
        implementation("androidx.compose.material:material-icons-extended:1.3.1")

        /* Polar SDK and dependencies */
        implementation("com.github.polarofficial:polar-ble-sdk:4.0.0")
        implementation("io.reactivex.rxjava3:rxjava:3.1.5")
        implementation("io.reactivex.rxjava3:rxandroid:3.0.0")

        /* kotlinx */
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.6.4")

    }

    sourceSets.invokeWhenCreated("androidDebug") {
        dependencies {
            implementation("androidx.compose.ui:ui-tooling")
            implementation("androidx.compose.ui:ui-test-manifest")
        }
    }

    sourceSets.androidInstrumentedTest.get().dependencies {
        implementation(platform("androidx.compose:compose-bom:2022.12.00"))
        implementation("androidx.compose.ui:ui-test-junit4")
    }

    /* Requried for 1.8.20-Beta */
    android().compilations.all {
        kotlinOptions {
            kotlinOptions {
                freeCompilerArgs += listOf(
                    "-P", "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true"
                )
            }
        }
    }
}
