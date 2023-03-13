plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()

    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
        mavenContent {
            includeGroupByRegex(".*compose.*")
        }
    }

    google {
        mavenContent {
            includeGroupByRegex(".*google.*")
            includeGroupByRegex(".*android.*")
        }
    }
}

dependencies {
    implementation("org.jetbrains.compose:compose-gradle-plugin:1.4.0-alpha01-dev972")
    implementation(kotlin("gradle-plugin:1.8.0"))
    implementation("com.android.tools.build:gradle:7.4.1")
}