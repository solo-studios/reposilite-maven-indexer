package ca.solostudios.reposilite.mvnindexer.infrastructure

import ca.solostudios.reposilite.mvnindexer.MavenIndexerComponents
import ca.solostudios.reposilite.mvnindexer.MavenIndexerSettings
import ca.solostudios.reposilite.mvnindexer.api.MavenIndexerSearchRequest
import ca.solostudios.reposilite.mvnindexer.api.MavenIndexerSearchResponse
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.maven.MavenFacade
import com.reposilite.maven.Repository
import com.reposilite.maven.api.LookupRequest
import com.reposilite.maven.api.PreResolveEvent
import com.reposilite.maven.api.ResolvedDocument
import com.reposilite.maven.api.ResolvedFileEvent
import com.reposilite.plugin.Extensions
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequest
import com.reposilite.shared.badRequestError
import com.reposilite.shared.notFound
import com.reposilite.shared.notFoundError
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageFacade
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import com.reposilite.storage.filesystem.FileSystemStorageProvider
import com.reposilite.storage.inputStream
import com.reposilite.token.AccessTokenIdentifier
import io.javalin.http.ContentType
import io.javalin.http.ContentType.APPLICATION_OCTET_STREAM
import kotlinx.datetime.Clock
import org.apache.lucene.index.MultiBits
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.maven.index.ArtifactContext
import org.apache.maven.index.ArtifactInfo
import org.apache.maven.index.ArtifactScanningListener
import org.apache.maven.index.Indexer
import org.apache.maven.index.IteratorSearchRequest
import org.apache.maven.index.NEXUS
import org.apache.maven.index.ScanningResult
import org.apache.maven.index.SearchType
import org.apache.maven.index.artifact.VersionUtils
import org.apache.maven.index.context.IndexUtils
import org.apache.maven.index.context.IndexingContext
import panda.std.Result
import panda.std.asSuccess
import panda.std.ok
import panda.std.reactive.Reference
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readAttributes
import kotlin.time.DurationUnit.MILLISECONDS


internal class MavenIndexerService(
    private val indexer: Indexer,
    private val journalist: Journalist,
    private val mavenFacade: MavenFacade,
    private val failureFacade: FailureFacade,
    private val storageFacade: StorageFacade,
    private val extensions: Extensions,
    settings: Reference<MavenIndexerSettings>,
    private val components: MavenIndexerComponents,
) : Journalist {
    private var scheduler: ScheduledExecutorService = components.scheduler()
    private lateinit var scheduledIndexingTask: ScheduledFuture<*>

    init {
        settings.subscribeDetailed(::settingsUpdate, true)
    }

    private fun settingsUpdate(oldSettings: MavenIndexerSettings, newSettings: MavenIndexerSettings) {
        if (::scheduledIndexingTask.isInitialized)
            scheduledIndexingTask.cancel(false)

        scheduler.shutdown()
        scheduler.awaitTermination(240L, TimeUnit.MINUTES)
        scheduler = components.scheduler()

        if (!newSettings.enabled)
            return

        val durationMillis = newSettings.mavenIndexFullScanInterval.duration.toLong(MILLISECONDS)
        val initialDelay = if (newSettings.development) 0 else durationMillis // TODO: 2024-11-05 Remove
        scheduledIndexingTask = scheduler.scheduleAtFixedRate(::incrementalIndex, initialDelay, durationMillis, TimeUnit.MILLISECONDS)
    }

    fun incrementalIndex(startPath: Location = Location.empty()): Result<Unit, ErrorResponse> {
        val repositories = mavenFacade.getRepositories().filter { it.storageProvider is FileSystemStorageProvider }
        return repositories.asSequence().map { repository ->
            incrementalIndex(repository, startPath).onError { error ->
                logger.error("MavenIndexerService | Error while indexing repository: {}", error.message)
            }
        }.firstOrNull { it.isErr } ?: ok()
    }

    fun incrementalIndex(repository: Repository, startPath: Location = Location.empty()): Result<Unit, ErrorResponse> {
        return repository.executeWithFsStorage { storageProvider ->
            indexRepositoryLocation(repository, storageProvider, startPath)
        }
    }

    fun rebuildIndex(repository: Repository, startPath: Location = Location.empty()): Result<Unit, ErrorResponse> {
        return purgeIndex(repository, startPath).flatMap {
            repository.executeWithFsStorage { storageProvider ->
                indexRepositoryLocation(repository, storageProvider, startPath)
            }
        }
    }

    fun purgeIndex(startPath: Location = Location.empty()): Result<Unit, ErrorResponse> {
        val repositories = mavenFacade.getRepositories().filter { it.storageProvider is FileSystemStorageProvider }
        return repositories.asSequence().map { repository ->
            purgeIndex(repository, startPath).onError { error ->
                logger.error("MavenIndexerService | Error while purging indexed repository: {}", error.message)
            }
        }.firstOrNull { it.isErr } ?: ok()
    }

    fun purgeIndex(repository: Repository, startPath: Location = Location.empty()): Result<Unit, ErrorResponse> {
        val startPath = startPath.toString()
        return repository.executeWithFsStorage { storageProvider ->
            val indexingContext = components.indexingContext(repository, indexer, storageProvider)
            try {
                indexer.searchIterator(IteratorSearchRequest(MatchAllDocsQuery(), listOf(indexingContext)) { context, other ->
                    context.gavCalculator.gavToPath(other.calculateGav()).startsWith(startPath)
                }).asSequence().map { info ->
                    ArtifactContext(null, null, null, info, info.calculateGav())
                }.chunked(32).forEach { artifacts ->
                    indexer.deleteArtifactsFromIndex(artifacts, indexingContext)
                }

                indexingContext.commit()
            } finally {
                indexer.closeIndexingContext(indexingContext, true)
            }
        }
    }

    fun search(searchRequest: MavenIndexerSearchRequest): Result<MavenIndexerSearchResponse, ErrorResponse> {
        TODO("Finish search impl")
    }

    fun contains(searchRequest: MavenIndexerSearchRequest): Result<Boolean, ErrorResponse> {
        TODO("Finish search impl")
    }

    fun findDetails(lookupRequest: LookupRequest): Result<FileDetails, ErrorResponse> {
        return resolve(lookupRequest) { repository, gav ->
            findLocalDetails(repository, gav)
        }.flatMapErr { mavenFacade.findDetails(lookupRequest) as Result<FileDetails, ErrorResponse> }
    }

    fun findFile(lookupRequest: LookupRequest): Result<ResolvedDocument, ErrorResponse> {
        return resolve(lookupRequest) { repository, gav ->
                findFile(lookupRequest.accessToken, repository, gav).map { (details, stream) ->
                    ResolvedDocument(document = details, cachable = repository.acceptsCachingOf(gav), content = stream)
                }
        }.flatMapErr { mavenFacade.findFile(lookupRequest) }
    }

    fun findInputStream(lookupRequest: LookupRequest): Result<InputStream, ErrorResponse> {
        return resolve(lookupRequest) { repository, gav ->
                findInputStream(repository, gav)
        }.flatMapErr { mavenFacade.findData(lookupRequest) }
    }

    fun shutdown() {
        scheduledIndexingTask.cancel(false)
        scheduler.shutdown()
    }

    private fun Repository.executeWithFsStorage(block: (FileSystemStorageProvider) -> Unit): Result<Unit, ErrorResponse> {
        val storageProvider = storageProvider

        return if (storageProvider !is FileSystemStorageProvider)
            badRequestError("Repository must be located on the local file system")
        else
            scheduler.execute {
                try {
                    block(storageProvider)
                } catch (e: Exception) {
                    failureFacade.throwException("MavenIndexerService | exception while executing scheduled task", e)
                }
            }.asSuccess()
    }

    private fun indexRepositoryLocation(
        repository: Repository,
        storageProvider: FileSystemStorageProvider,
        startPath: Location,
    ) {
        logger.info("MavenIndexerService | Indexing the {} repository", repository.name)
        val indexingContext = components.indexingContext(repository, indexer, storageProvider)

        try {
            val scanner = components.scanner()
            val searcher = indexingContext.acquireIndexSearcher()

            logger.debug("MavenIndexerService | Scanning the {} repository", repository.name)
            scanner.scan(components.scanningRequest(indexingContext, ReindexArtifactScanningListener(indexingContext), startPath))

            try {
                logger.debug("MavenIndexerService | Packing the {} repository", repository.name)
                val packer = components.indexPacker()

                packer.packIndex(components.indexPackingRequest(indexingContext, searcher, repository))

                indexingContext.commit()
            } catch (e: Exception) {
                failureFacade.throwException("MavenIndexerService | Could not pack index for ${repository.name}", e)
            } finally {
                indexingContext.releaseIndexSearcher(searcher)
            }
        } finally {
            indexer.closeIndexingContext(indexingContext, false)
        }
    }

    private fun <T> resolve(
        lookupRequest: LookupRequest,
        block: (Repository, Location) -> Result<T, ErrorResponse>
    ): Result<T, ErrorResponse> {
        val (accessToken, repositoryName, gav) = lookupRequest
        val repository = mavenFacade.getRepository(repositoryName) ?: return notFoundError("Repository $repositoryName not found")

        return mavenFacade.canAccessResource(accessToken, repository, gav)
            .onError { logger.debug("ACCESS | Unauthorized attempt of access (token: $accessToken) to $gav from ${repository.name}") }
            .peek { extensions.emitEvent(PreResolveEvent(accessToken, repository, gav)) }
            .flatMap { block(repository, gav) }
    }

    private fun findFile(
        accessToken: AccessTokenIdentifier?,
        repository: Repository,
        gav: Location
    ): Result<Pair<DocumentInfo, InputStream>, ErrorResponse> {
        return findLocalDetails(repository, gav)
            .`is`(DocumentInfo::class.java) { notFound("Requested file is a directory") }
            .flatMap { details -> findInputStream(repository, gav).map { details to it } }
            .let { extensions.emitEvent(ResolvedFileEvent(accessToken, repository, gav, it)).result }
    }

    private fun findInputStream(repository: Repository, gav: Location): Result<InputStream, ErrorResponse> {
        return gav.resolveWithRootDirectory(repository).exists().flatMap { resource -> resource.inputStream() }
    }

    private fun findLocalDetails(
        repository: Repository,
        gav: Location
    ): Result<FileDetails, ErrorResponse> {
        return getFileDetails(repository, gav).map { it }
    }

    private fun getFileDetails(repository: Repository, location: Location): Result<DocumentInfo, ErrorResponse> {
        return location.resolveWithRootDirectory(repository)
            .exists()
            .flatMap {
                if (it.exists() && it.isRegularFile())
                    it.toDocumentInfo()
                else
                    notFoundError("Cannot find '$location' in maven index")
            }
    }

    private fun Result<Path, ErrorResponse>.exists() = filter({ Files.exists(it) }) { notFound("Cannot find '$it' in maven index") }

    private fun Location.resolveWithRootDirectory(repository: Repository): Result<Path, ErrorResponse> {
        return toPath().map { components.mavenIndexPath(repository).resolve(it) }.mapErr { badRequest(it) }
    }

    private fun Path.toDocumentInfo(): Result<DocumentInfo, ErrorResponse> {
        val attributes = readAttributes<BasicFileAttributes>()
        val contentType = ContentType.getContentTypeByExtension(extension) ?: APPLICATION_OCTET_STREAM
        val lastModified = attributes.lastModifiedTime().toInstant()

        return DocumentInfo(name, contentType, attributes.size(), lastModified).asSuccess()
    }

    override fun getLogger(): Logger = journalist.logger

    private inner class ReindexArtifactScanningListener(
        private val context: IndexingContext,
    ) : ArtifactScanningListener {
        private val startTime = Clock.System.now()
        private val artifactsToProcess = mutableMapOf<String, ArtifactInfo>()
        private val processedUinfos = mutableSetOf<String>()
        private val groupIds = mutableSetOf<String>()
        private val rootGroups = mutableSetOf<String>()
        private val exceptions = mutableListOf<Exception>()

        private var totalFiles: Int = 0
        private var deletedFiles: Int = 0

        override fun scanningStarted(context: IndexingContext) {
            try {
                initialize(context)
            } catch (ex: IOException) {
                exceptions += ex
            }
        }

        private fun initialize(context: IndexingContext) {
            val indexSearcher = context.acquireIndexSearcher()
            try {
                val reader = indexSearcher.indexReader
                val liveDocs = MultiBits.getLiveDocs(reader)

                for (documentId in 0 until reader.maxDoc()) {
                    if (liveDocs == null || liveDocs[documentId]) {
                        val document = reader.storedFields().document(documentId)

                        val artifactInfo = IndexUtils.constructArtifactInfo(document, context)

                        if (artifactInfo != null && !context.isReceivingUpdates)
                            artifactsToProcess[artifactInfo.uinfo] = artifactInfo // if not receiving external updates
                    }
                }
            } finally {
                context.releaseIndexSearcher(indexSearcher)
            }
        }

        override fun artifactError(artifactContext: ArtifactContext, exception: Exception) {
            exceptions += exception
        }

        override fun artifactDiscovered(artifactContext: ArtifactContext) {
            logger.debug("MavenIndexerService | discovered artifact {}", context.gavCalculator.gavToPath(artifactContext.gav))

            val artifactInfo = artifactContext.artifactInfo

            if (VersionUtils.isSnapshot(artifactInfo.version) && artifactInfo.uinfo in processedUinfos)
                return // skip all snapshots other than the first one

            processedUinfos += artifactInfo.uinfo

            if (artifactInfo.uinfo in artifactsToProcess)
                artifactsToProcess -= artifactInfo.uinfo

            try {
                indexer.addArtifactToIndex(artifactContext, context)

                exceptions += artifactContext.errors

                rootGroups += artifactInfo.rootGroup
                groupIds += artifactInfo.groupId

                totalFiles += 1
            } catch (e: Exception) {
                exceptions += e
            }
        }

        override fun scanningFinished(context: IndexingContext, result: ScanningResult) {
            context.setRootGroups(rootGroups)
            context.setAllGroups(groupIds)

            try {
                context.commit()
            } catch (e: Exception) {
                exceptions += e
            }

            try {
                if (!context.isReceivingUpdates)
                    removeDeletedArtifacts(context, result.request.startingPath)
            } catch (e: Exception) {
                exceptions += e
            }

            try {
                context.commit()
            } catch (e: Exception) {
                exceptions += e
            }

            logger.debug(
                "MavenIndexerService | Scanning finished, indexed {} files, removed {} stale files in {}",
                totalFiles,
                deletedFiles,
                startTime - Clock.System.now()
            )

            if (exceptions.isNotEmpty()) {
                logger.error("MavenIndexerService | Scanning produced {} exceptions", exceptions.size)

                for (exception in exceptions)
                    failureFacade.throwException("MavenIndexerService | Exception while indexing:", exception)
            }
        }

        private fun removeDeletedArtifacts(context: IndexingContext, contextPath: String) {
            // remove all artifacts that could not be located
            // this *might* gobble up a bunch of memory? unsure.
            val artifactsToRemove = artifactsToProcess.asSequence().flatMap { (_, artifactInfo) ->
                val query = indexer.constructQuery(NEXUS.UINFO, artifactInfo.uinfo, SearchType.EXACT)
                indexer.searchIterator(IteratorSearchRequest(query, listOf(context)) { _, other ->
                    other.uinfo == artifactInfo.uinfo
                }).asSequence() + artifactInfo
            }.filter { artifactInfo ->
                context.gavCalculator.gavToPath(artifactInfo.calculateGav()).startsWith(contextPath)
            }.distinctBy { artifactInfo ->
                artifactInfo.uinfo
            }.map { artifactInfo ->
                ArtifactContext(null, null, null, artifactInfo, artifactInfo.calculateGav())
            }.toList()

            indexer.deleteArtifactsFromIndex(artifactsToRemove, context)
            deletedFiles = artifactsToRemove.size

            if (deletedFiles > 0) {
                context.commit()
            }
        }
    }
}
