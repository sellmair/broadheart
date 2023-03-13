@file:Suppress("OPT_IN_USAGE")

import com.android.build.gradle.api.AndroidBasePlugin
import org.gradle.kotlin.dsl.kotlin

plugins {
    kotlin("multiplatform")
    id("android-conventions")
}

kotlin {
    plugins.withType<AndroidBasePlugin>().configureEach {
        android()
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()

    /* Waiting for 1.8.20
    targetHierarchy.default {
        common {
            group("mobile") {
                withAndroid()
                withJvm()
                withIos()
            }
        }
    }
     */

    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
    }

    jvmToolchain(8)
}