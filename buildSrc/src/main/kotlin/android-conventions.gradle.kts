import   com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.AndroidBasePlugin

plugins.withType<AndroidBasePlugin>().configureEach {
    extensions.configure<BaseExtension> {
        compileSdkVersion(33)
        defaultConfig {
            minSdk = 29
        }
    }
}