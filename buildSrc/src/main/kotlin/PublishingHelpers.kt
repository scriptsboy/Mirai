/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress(
    "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "NOTHING_TO_INLINE", "RemoveRedundantBackticks",
    "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS"
)

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.ExistingDomainObjectDelegate
import org.gradle.kotlin.dsl.RegisteringDomainObjectDelegateProviderWithTypeAndAction
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KProperty

/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

/**
 * Configures the [bintray][com.jfrog.bintray.gradle.BintrayExtension] extension.
 */
@PublishedApi
internal fun Project.`bintray`(configure: com.jfrog.bintray.gradle.BintrayExtension.() -> Unit): Unit =
    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("bintray", configure)

@PublishedApi
internal operator fun <U : Task> RegisteringDomainObjectDelegateProviderWithTypeAndAction<out TaskContainer, U>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate.of(
    delegateProvider.register(property.name, type.java, action)
)

@PublishedApi
internal val Project.`sourceSets`: org.gradle.api.tasks.SourceSetContainer
    get() =
        (this as org.gradle.api.plugins.ExtensionAware).extensions.getByName("sourceSets") as org.gradle.api.tasks.SourceSetContainer

@PublishedApi
internal operator fun <T> ExistingDomainObjectDelegate<out T>.getValue(receiver: Any?, property: KProperty<*>): T =
    delegate

/**
 * Configures the [publishing][org.gradle.api.publish.PublishingExtension] extension.
 */
@OptIn(ExperimentalContracts::class)
@PublishedApi
internal fun Project.`publishing`(configure: org.gradle.api.publish.PublishingExtension.() -> Unit): Unit {
    contract {
        callsInPlace(configure, InvocationKind.EXACTLY_ONCE)
    }
    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("publishing", configure)
}


val Project.publications: PublicationContainer
    get() {
        val ret: PublicationContainer
        publishing {
            ret = publications
        }
        return ret
    }

fun MavenPublication.setupPom(
    project: Project,
    vcs: String = "https://github.com/mamoe/mirai"
) {
    pom {
        scm {
            url.set(vcs)
            connection.set("scm:$vcs.git")
            developerConnection.set("scm:${vcs.replace("https:", "git:")}.git")
        }

        licenses {
            license {
                name.set("GNU AGPLv3")
                url.set("https://github.com/mamoe/mirai/blob/master/LICENSE")
            }
        }

        developers {
            developer {
                id.set("mamoe")
                name.set("Mamoe Technologies")
                email.set("support@mamoe.net")
            }
        }

    }

    pom.withXml {
        val root = asNode()
        root.appendNode("description", project.description)
        root.appendNode("name", project.name)
        root.appendNode("url", vcs)
    }
}

