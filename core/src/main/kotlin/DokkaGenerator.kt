package org.jetbrains.dokka

import org.jetbrains.dokka.Model.Module
import org.jetbrains.dokka.Model.transformers.DocumentationNodesMerger
import org.jetbrains.dokka.Utilities.pretty
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.pages.MarkdownToContentConverter
import org.jetbrains.dokka.pages.PlatformData
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.renderers.FileWriter
import org.jetbrains.dokka.renderers.HtmlRenderer
import org.jetbrains.dokka.resolvers.DefaultLocationProvider
import org.jetbrains.dokka.transformers.DefaultDocumentationToPageTransformer
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File

class DokkaGenerator(
    private val configuration: DokkaConfiguration,
    private val logger: DokkaLogger
) {
    fun generate(): Unit {

        logger.debug("Initializing plugins")
        val context = DokkaContext.from(configuration.pluginsClasspath)
        context.pluginNames.also { names ->
            logger.progress("Loaded plugins: $names")
            names.groupingBy { it }.eachCount().filter { it.value > 1 }.forEach {
                logger.warn("Duplicate plugin name: ${it.key}. It will make debugging much harder.")
            }
        }



        configuration.passesConfigurations.map { pass ->
            AnalysisEnvironment(DokkaMessageCollector(logger), pass.analysisPlatform).run {
                if (analysisPlatform == Platform.jvm) {
                    addClasspath(PathUtil.getJdkClassesRootsFromCurrentJre())
                }
                for (element in pass.classpath) {
                    addClasspath(File(element))
                }

                addSources(pass.sourceRoots.map { it.path })

                loadLanguageVersionSettings(pass.languageVersion, pass.apiVersion)

                val environment = createCoreEnvironment()
                val (facade, _) = createResolutionFacade(environment)

                environment.getSourceFiles().asSequence()
                    .map { it.packageFqName }
                    .distinct()
                    .mapNotNull { facade.resolveSession.getPackageFragment(it) }
                    .map {
                        DokkaDescriptorVisitor(PlatformData(pass.analysisPlatform, pass.targets), facade).visitPackageFragmentDescriptor(
                            it,
                            DRI.topLevel
                        )
                    }
                    .toList()
                    .let { Module(it) }
                    .let { DocumentationNodesMerger(it) }
                    .also { println("${pass.analysisPlatform}:\n${it.pretty()}\n\n") }
            }
        }.let {
            val markdownConverter = MarkdownToContentConverter(logger)
//            it.forEach { it.genericPretty().also(::nierzigoj) }
            DefaultDocumentationToPageTransformer(markdownConverter, logger).transform(DocumentationNodesMerger(it))
        }
            .also {
//                it.genericPretty().also(::nierzigoj)
                HtmlRenderer(
                    FileWriter(configuration.outputDir, ""),
                    DefaultLocationProvider(it, configuration, ".${configuration.format}")
                ).render(it)
            }
    }
}

private fun nierzigoj(niczym: String) {}

private class DokkaMessageCollector(private val logger: DokkaLogger) : MessageCollector {
    override fun clear() {
        seenErrors = false
    }

    private var seenErrors = false

    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation?) {
        if (severity == CompilerMessageSeverity.ERROR) {
            seenErrors = true
        }
        logger.error(MessageRenderer.PLAIN_FULL_PATHS.render(severity, message, location))
    }

    override fun hasErrors() = seenErrors
}