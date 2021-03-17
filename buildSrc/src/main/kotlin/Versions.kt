/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("ObjectPropertyName", "ObjectPropertyName", "unused", "MemberVisibilityCanBePrivate")

import org.gradle.api.attributes.Attribute

object Versions {
    const val project = "2.5-M2"

    const val core = project
    const val console = project
    const val consoleTerminal = project

    const val kotlinCompiler = "1.4.30"
    const val kotlinStdlib = "1.4.30"
    const val dokka = "1.4.20"

    const val coroutines = "1.4.2"
    const val atomicFU = "0.15.1"
    const val serialization = "1.1.0-RC"
    const val ktor = "1.5.1"

    const val binaryValidator = "0.4.0"

    const val io = "0.1.16"
    const val coroutinesIo = "0.1.16"

    const val blockingBridge = "1.10.0"

    const val androidGradlePlugin = "4.1.1"
    const val android = "4.1.1.4"

    const val bintray = "1.8.5"
    const val shadow = "6.1.0"

    const val slf4j = "1.7.30"
    const val log4j = "2.13.3"
    const val asm = "9.1"


    // If you the versions below, you need to sync changes to mirai-console/buildSrc/src/main/kotlin/Versions.kt

    const val yamlkt = "0.7.5"
    const val intellijGradlePlugin = "0.4.16"
    const val kotlinIntellijPlugin = "203-1.4.30-release-IJ7148.5" // keep to newest as kotlinCompiler
    const val intellij = "2020.3.2" // don't update easily unless you want your disk space -= 500MB

}

@Suppress("unused")
fun kotlinx(id: String, version: String) = "org.jetbrains.kotlinx:kotlinx-$id:$version"

@Suppress("unused")
fun ktor(id: String, version: String = Versions.ktor) = "io.ktor:ktor-$id:$version"


val `kotlinx-coroutines-core` = kotlinx("coroutines-core", Versions.coroutines)
val `kotlinx-coroutines-jdk8` = kotlinx("coroutines-jdk8", Versions.coroutines)
val `kotlinx-serialization-core` = kotlinx("serialization-core", Versions.serialization)
val `kotlinx-serialization-json` = kotlinx("serialization-json", Versions.serialization)
val `kotlinx-serialization-protobuf` = kotlinx("serialization-protobuf", Versions.serialization)
const val `kotlinx-atomicfu` = "org.jetbrains.kotlinx:atomicfu:${Versions.atomicFU}"
val `kotlinx-io` = kotlinx("io", Versions.io)
val `kotlinx-io-jvm` = kotlinx("io-jvm", Versions.io)
val `kotlinx-coroutines-io` = kotlinx("coroutines-io", Versions.coroutinesIo)
val `kotlinx-coroutines-io-jvm` = kotlinx("coroutines-io-jvm", Versions.coroutinesIo)

val `ktor-serialization` = ktor("serialization", Versions.ktor)

val `ktor-client-core` = ktor("client-core", Versions.ktor)
val `ktor-client-cio` = ktor("client-cio", Versions.ktor)
val `ktor-client-okhttp` = ktor("client-okhttp", Versions.ktor)
val `ktor-client-android` = ktor("client-android", Versions.ktor)
val `ktor-network` = ktor("network", Versions.ktor)
val `ktor-client-serialization-jvm` = ktor("client-serialization-jvm", Versions.ktor)

const val slf4j = "org.slf4j:slf4j-api:" + Versions.slf4j
const val `log4j-api` = "org.apache.logging.log4j:log4j-api:" + Versions.log4j

val ATTRIBUTE_MIRAI_TARGET_PLATFORM: Attribute<String> = Attribute.of("mirai.target.platform", String::class.java)


const val `kotlin-compiler` = "org.jetbrains.kotlin:kotlin-compiler:${Versions.kotlinCompiler}"

const val `kotlin-stdlib` = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlinStdlib}"
const val `kotlin-stdlib-jdk8` = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Versions.kotlinStdlib}"
const val `kotlin-reflect` = "org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlinStdlib}"
const val `kotlin-test` = "org.jetbrains.kotlin:kotlin-test:${Versions.kotlinStdlib}"
const val `kotlin-test-junit5` = "org.jetbrains.kotlin:kotlin-test-junit5:${Versions.kotlinStdlib}"


const val `mirai-core-api` = "net.mamoe:mirai-core-api:${Versions.core}"
const val `mirai-core` = "net.mamoe:mirai-core:${Versions.core}"
const val `mirai-core-utils` = "net.mamoe:mirai-core-utils:${Versions.core}"

const val yamlkt = "net.mamoe.yamlkt:yamlkt:${Versions.yamlkt}"

const val `jetbrains-annotations` = "org.jetbrains:annotations:19.0.0"


const val `caller-finder` = "io.github.karlatemp:caller:1.1.1"

const val `android-runtime` = "com.google.android:android:${Versions.android}"