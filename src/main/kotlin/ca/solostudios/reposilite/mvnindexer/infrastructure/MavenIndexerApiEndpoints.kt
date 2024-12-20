package ca.solostudios.reposilite.mvnindexer.infrastructure

import ca.solostudios.reposilite.mvnindexer.MavenIndexerFacade
import com.reposilite.maven.infrastructure.MavenRoutes
import com.reposilite.web.api.ReposiliteRoute
import io.javalin.community.routing.Route
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse

internal class MavenIndexerApiEndpoints(
    private val mavenIndexerFacade: MavenIndexerFacade,
) : MavenRoutes(mavenIndexerFacade.mavenFacade) {
    @OpenApi(
        tags = ["MavenIndexer"],
        path = "/api/maven-indexer/{repository}/index",
        methods = [HttpMethod.POST],
        pathParams = [
            OpenApiParam(name = "repository", description = "Destination repository", required = true, allowEmptyValue = true),
        ],
        responses = [
            OpenApiResponse(status = "200", description = "Returns 200 if the request was successful"),
            OpenApiResponse(status = "403", description = "Returns 403 for invalid credentials"),
        ]
    )
    private val indexRepository = ReposiliteRoute<Unit>("/api/maven-indexer/{repository}/index", Route.POST) {
        managerOnly {
            requireRepository { repository ->
                response = mavenIndexerFacade.indexRepository(repository)
            }
        }
    }

    @OpenApi(
        tags = ["MavenIndexer"],
        path = "/api/maven-indexer/{repository}/index/<gav>",
        methods = [HttpMethod.POST],
        pathParams = [
            OpenApiParam(name = "repository", description = "Destination repository", required = true, allowEmptyValue = true),
            OpenApiParam(name = "gav", description = "Artifact path qualifier", required = true, allowEmptyValue = true)
        ],
        responses = [
            OpenApiResponse(status = "200", description = "Returns 200 if the request was successful"),
            OpenApiResponse(status = "403", description = "Returns 403 for invalid credentials"),
        ]
    )
    private val indexRepositoryGav = ReposiliteRoute<Unit>("/api/maven-indexer/{repository}/index/<gav>", Route.POST) {
        managerOnly {
            requireRepository { repository ->
                requireGav { gav ->
                    response = mavenIndexerFacade.indexRepository(repository, gav)
                }
            }
        }
    }

    @OpenApi(
        tags = ["MavenIndexer"],
        path = "/api/maven-indexer/index-all",
        methods = [HttpMethod.POST],
        responses = [
            OpenApiResponse(status = "200", description = "Returns 200 if the request was successful"),
            OpenApiResponse(status = "403", description = "Returns 403 for invalid credentials"),
        ]
    )
    private val indexAll = ReposiliteRoute<Unit>("/api/maven-indexer/index-all", Route.POST) {
        managerOnly {
            response = mavenIndexerFacade.indexAllRepositories()
        }
    }

    @OpenApi(
        tags = ["MavenIndexer"],
        path = "/api/maven-indexer/index-all/<gav>",
        methods = [HttpMethod.POST],
        pathParams = [
            OpenApiParam(name = "gav", description = "Artifact path qualifier", required = true, allowEmptyValue = true)
        ],
        responses = [
            OpenApiResponse(status = "200", description = "Returns 200 if the request was successful"),
            OpenApiResponse(status = "403", description = "Returns 403 for invalid credentials"),
        ]
    )
    private val indexAllGav = ReposiliteRoute<Unit>("/api/maven-indexer/index-all/<gav>", Route.POST) {
        managerOnly {
            requireGav { gav ->
                response = mavenIndexerFacade.indexAllRepositories(gav)
            }
        }
    }

    @OpenApi(
        tags = ["MavenIndexer"],
        path = "/api/maven-indexer/{repository}/index",
        methods = [HttpMethod.DELETE],
        pathParams = [
            OpenApiParam(name = "repository", description = "Destination repository", required = true, allowEmptyValue = true),
        ],
        responses = [
            OpenApiResponse(status = "200", description = "Returns 200 if the request was successful"),
            OpenApiResponse(status = "403", description = "Returns 403 for invalid credentials"),
        ]
    )
    private val deleteRepository = ReposiliteRoute<Unit>("/api/maven-indexer/{repository}/index", Route.DELETE) {
        managerOnly {
            requireRepository { repository ->
                response = mavenIndexerFacade.purgeRepository(repository)
            }
        }
    }

    @OpenApi(
        tags = ["MavenIndexer"],
        path = "/api/maven-indexer/{repository}/index/<gav>",
        methods = [HttpMethod.DELETE],
        pathParams = [
            OpenApiParam(name = "repository", description = "Destination repository", required = true, allowEmptyValue = true),
            OpenApiParam(name = "gav", description = "Artifact path qualifier", required = true, allowEmptyValue = true)
        ],
        responses = [
            OpenApiResponse(status = "200", description = "Returns 200 if the request was successful"),
            OpenApiResponse(status = "403", description = "Returns 403 for invalid credentials"),
        ]
    )
    private val deleteRepositoryGav = ReposiliteRoute<Unit>("/api/maven-indexer/{repository}/index/<gav>", Route.DELETE) {
        managerOnly {
            requireRepository { repository ->
                requireGav { gav ->
                    response = mavenIndexerFacade.purgeRepository(repository, gav)
                }
            }
        }
    }

    @OpenApi(
        tags = ["MavenIndexer"],
        path = "/api/maven-indexer/index-all",
        methods = [HttpMethod.DELETE],
        responses = [
            OpenApiResponse(status = "200", description = "Returns 200 if the request was successful"),
            OpenApiResponse(status = "403", description = "Returns 403 for invalid credentials"),
        ]
    )
    private val deleteAll = ReposiliteRoute<Unit>("/api/maven-indexer/index-all", Route.DELETE) {
        managerOnly {
            response = mavenIndexerFacade.purgeAllRepositories()
        }
    }

    @OpenApi(
        tags = ["MavenIndexer"],
        path = "/api/maven-indexer/index-all/<gav>",
        methods = [HttpMethod.DELETE],
        pathParams = [
            OpenApiParam(name = "gav", description = "Artifact path qualifier", required = true, allowEmptyValue = true)
        ],
        responses = [
            OpenApiResponse(status = "200", description = "Returns 200 if the request was successful"),
            OpenApiResponse(status = "403", description = "Returns 403 for invalid credentials"),
        ]
    )
    private val deleteAllGav = ReposiliteRoute<Unit>("/api/maven-indexer/index-all/<gav>", Route.DELETE) {
        managerOnly {
            requireGav { gav ->
                response = mavenIndexerFacade.purgeAllRepositories(gav)
            }
        }
    }

    override val routes = routes(
        indexRepository, indexRepositoryGav, indexAll, indexAllGav,
        deleteRepository, deleteRepositoryGav, deleteAll, deleteAllGav,
    )
}

