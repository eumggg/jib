buildscript {
    // JavaPoet 1.13.0 must appear early on the daemon classpath so Hilt's
    // AggregateDepsTask (NoIsolation worker) resolves ClassName.canonicalName()
    // which was added in 1.13.0 and is shadowed by older AGP-bundled versions.
    dependencies {
        classpath("com.squareup:javapoet:1.13.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
}
