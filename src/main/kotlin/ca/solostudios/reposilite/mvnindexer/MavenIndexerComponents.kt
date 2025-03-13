package ca.solostudios.reposilite.mvnindexer

import ca.solostudios.reposilite.mvnindexer.index.creator.MavenExtraArtifactInfoIndexCreator
import ca.solostudios.reposilite.mvnindexer.infrastructure.MavenIndexerService
import com.reposilite.Reposilite
import com.reposilite.journalist.Journalist
import com.reposilite.maven.MavenFacade
import com.reposilite.maven.Repository
import com.reposilite.plugin.api.PluginComponents
import com.reposilite.shared.extensions.NamedThreadFactory
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageFacade
import com.reposilite.storage.api.Location
import com.reposilite.storage.filesystem.FileSystemStorageProvider
import org.apache.lucene.search.IndexSearcher
import org.apache.maven.index.ArtifactScanningListener
import org.apache.maven.index.DefaultArtifactContextProducer
import org.apache.maven.index.DefaultIndexer
import org.apache.maven.index.DefaultIndexerEngine
import org.apache.maven.index.DefaultQueryCreator
import org.apache.maven.index.DefaultScanner
import org.apache.maven.index.DefaultSearchEngine
import org.apache.maven.index.Indexer
import org.apache.maven.index.IndexerEngine
import org.apache.maven.index.ScanningRequest
import org.apache.maven.index.artifact.DefaultArtifactPackagingMapper
import org.apache.maven.index.context.IndexCreator
import org.apache.maven.index.context.IndexingContext
import org.apache.maven.index.creator.JarFileContentsIndexCreator
import org.apache.maven.index.creator.MavenArchetypeArtifactInfoIndexCreator
import org.apache.maven.index.creator.MavenPluginArtifactInfoIndexCreator
import org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator
import org.apache.maven.index.creator.OsgiArtifactIndexCreator
import org.apache.maven.index.incremental.DefaultIncrementalHandler
import org.apache.maven.index.packer.DefaultIndexPacker
import org.apache.maven.index.packer.IndexPackingRequest
import org.apache.maven.index.packer.IndexPackingRequest.IndexFormat
import panda.std.reactive.Reference
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

internal class MavenIndexerComponents(
    private val reposilite: Reposilite,
    private val journalist: Journalist,
    private val failureFacade: FailureFacade,
    private val storageFacade: StorageFacade,
    private val mavenFacade: MavenFacade,
    private val mavenIndexerSettings: Reference<MavenIndexerSettings>
) : PluginComponents {
    private val indexPath = mavenIndexerSettings
        .computed { it.indexPath }
        .computed { Path(".", it) }

    fun indexPath(repository: Repository, name: String? = null): Path {
        val path = indexPath.get().resolve(repository.name)
        return if (name == null) path else path.resolve(name)
    }

    fun mavenIndexPath(repository: Repository): Path = indexPath(repository, "maven-index")
    fun searchIndexPath(repository: Repository): Path = indexPath(repository, "search-index")

    fun indexingContext(
        repository: Repository,
        indexer: Indexer,
        indexers: List<IndexCreator>,
        fsProvider: FileSystemStorageProvider,
    ) = indexer.createIndexingContext(
        repository.name,
        repository.name,
        fsProvider.rootDirectory.toFile(),
        searchIndexPath(repository).createDirectories().toFile(),
        null,
        null,
        settings().searchable,
        false,
        indexers,
    )

    fun scanner() = DefaultScanner(artifactContextProducer())

    fun scanningRequest(
        indexingContext: IndexingContext,
        listener: ArtifactScanningListener,
        startingPath: Location,
    ) = ScanningRequest(indexingContext, listener, startingPath.toString())

    fun indexPackingRequest(
        indexingContext: IndexingContext,
        searcher: IndexSearcher,
        repository: Repository,
    ) = IndexPackingRequest(
        indexingContext,
        searcher.indexReader,
        mavenIndexPath(repository).createDirectories().toFile(),
    ).also { request ->
        request.isCreateChecksumFiles = settings().createChecksumFiles
        request.isCreateIncrementalChunks = settings().incrementalChunks
        request.maxIndexChunks = settings().incrementalChunksCount
        request.formats = listOf(IndexFormat.FORMAT_V1)
    }

    fun indexPacker() = DefaultIndexPacker(incrementalHandler())

    fun mavenIndexerFacade() = MavenIndexerFacade(
        journalist = journalist,
        mavenFacade = mavenFacade,
        mavenIndexerService = mavenIndexerService(),
    )

    fun scheduler(): ScheduledExecutorService {
        val maxThreads = settings().maxParallelIndexRepositories
        return Executors.newScheduledThreadPool(maxThreads, NamedThreadFactory("Maven Indexer ($maxThreads) - "))
    }

    fun indexCreators(indexers: MavenIndexerSettings.EnabledIndexersSettings) = buildList {
        if (indexers.minimal || indexers.mavenExtra || indexers.mavenArchetype || indexers.mavenPlugin)
            add(MinimalArtifactInfoIndexCreator())

        if (indexers.mavenExtra)
            add(MavenExtraArtifactInfoIndexCreator())

        if (indexers.mavenArchetype)
            add(MavenArchetypeArtifactInfoIndexCreator())

        if (indexers.mavenPlugin)
            add(MavenPluginArtifactInfoIndexCreator())

        if (indexers.osgiMetadata)
            add(OsgiArtifactIndexCreator())

        if (indexers.jarContent)
            add(JarFileContentsIndexCreator())
    }

    private fun artifactContextProducer() = DefaultArtifactContextProducer(artifactPackagingMapper())

    private fun artifactPackagingMapper() = DefaultArtifactPackagingMapper()

    private fun incrementalHandler() = DefaultIncrementalHandler()

    private fun mavenIndexer(engine: IndexerEngine) = DefaultIndexer(searchEngine(), engine, queryCreator())

    private fun searchEngine() = DefaultSearchEngine()

    private fun indexerEngine() = DefaultIndexerEngine()

    private fun queryCreator() = DefaultQueryCreator()

    private fun mavenIndexerService(): MavenIndexerService {
        val engine = indexerEngine()

        return MavenIndexerService(
            indexer = mavenIndexer(engine),
            indexerEngine = engine,
            journalist = journalist,
            mavenFacade = mavenFacade,
            failureFacade = failureFacade,
            storageFacade = storageFacade,
            settings = mavenIndexerSettings,
            components = this,
            extensions = reposilite.extensions
        )
    }

    private fun settings(): MavenIndexerSettings = mavenIndexerSettings.get()
}
