package ca.solostudios.reposilite.mvnindexer

import ca.solostudios.reposilite.mvnindexer.infrastructure.MavenIndexerService
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.maven.MavenFacade
import com.reposilite.maven.Repository
import com.reposilite.maven.api.LookupRequest
import com.reposilite.maven.api.ResolvedDocument
import com.reposilite.plugin.api.Facade
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import panda.std.Result
import java.io.InputStream

public class MavenIndexerFacade internal constructor(
    public val journalist: Journalist,
    public val mavenFacade: MavenFacade,
    private val mavenIndexerService: MavenIndexerService,
) : Journalist, Facade {
    public fun indexRepository(
        repository: Repository,
        startPath: Location = Location.empty(),
        rebuild: Boolean = false
    ): Result<Unit, ErrorResponse> {
        return if (rebuild)
            mavenIndexerService.rebuildIndex(repository, startPath, mavenIndexerService.allIndexers())
        else
            mavenIndexerService.incrementalIndex(repository, startPath, mavenIndexerService.allIndexers())
    }

    public fun indexAllRepositories(startPath: Location = Location.empty()): Result<Unit, ErrorResponse> {
        return mavenIndexerService.incrementalIndex(startPath, mavenIndexerService.allIndexers())
    }

    public fun purgeRepository(
        repository: Repository,
        startPath: Location = Location.empty(),
        rebuild: Boolean = false
    ): Result<Unit, ErrorResponse> {
        return if (rebuild)
            mavenIndexerService.rebuildIndex(repository, startPath, mavenIndexerService.allIndexers())
        else
            mavenIndexerService.purgeIndex(repository, startPath)
    }

    public fun purgeAllRepositories(startPath: Location = Location.empty()): Result<Unit, ErrorResponse> {
        return mavenIndexerService.purgeIndex(startPath)
    }

    public fun findDetails(lookupRequest: LookupRequest): Result<out FileDetails, ErrorResponse> {
        return mavenIndexerService.findDetails(lookupRequest)
    }

    public fun findFile(lookupRequest: LookupRequest): Result<ResolvedDocument, ErrorResponse> {
        return mavenIndexerService.findFile(lookupRequest)
    }

    public fun findData(lookupRequest: LookupRequest): Result<InputStream, ErrorResponse> {
        return mavenIndexerService.findInputStream(lookupRequest)
    }

    internal fun shutdown() = mavenIndexerService.stopIndexingTasks()

    override fun getLogger(): Logger = journalist.logger
}
