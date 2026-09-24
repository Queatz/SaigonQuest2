plugins {
    alias(libs.plugins.kotlinMultiplatform)

}

kotlin {
    jvm()

    js {
        browser()
    }


    sourceSets {
        commonMain.dependencies {
            // put your Multiplatform dependencies here
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
