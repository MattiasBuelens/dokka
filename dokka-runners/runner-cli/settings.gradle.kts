/*
 * Copyright 2014-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UnstableApiUsage")

rootProject.name = "runner-cli"

pluginManagement {
    includeBuild("../../build-logic")
    includeBuild("../../build-settings-logic")

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
    id("dokkasettings.gradle-enterprise")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }

    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
