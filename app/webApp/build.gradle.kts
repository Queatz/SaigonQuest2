plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    js {
        outputModuleName = "webApp"
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
                outputFileName = "webApp.js"
            }
        }
        binaries.executable()
        compilerOptions {
            target = "es2015"
        }
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":app:sharedLogic"))
            implementation(libs.wrappers.browser)
        }
        jsTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
