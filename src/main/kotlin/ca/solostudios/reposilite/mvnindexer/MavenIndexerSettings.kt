package ca.solostudios.reposilite.mvnindexer

import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.Min
import com.reposilite.configuration.shared.api.SharedSettings
import io.javalin.openapi.JsonSchema
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@JsonSchema(requireNonNulls = false)
@Doc(
    title = "Maven Indexer",
    description = """
        Maven Indexer module configuration.
    """
)
public data class MavenIndexerSettings(
    @get:Doc(
        title = "Enabled",
        description = """
            If building the maven indexes is enabled.<br>
            Defaults to false.
        """
    )
    val enabled: Boolean = false,
    @get:Doc(
        title = "Searchable",
        description = """
            Enable searching via Maven Indexer.<br>
            Defaults to false.
        """
    )
    val searchable: Boolean = false, // TODO: 2025-03-12 Implement this
    @get:Doc(
        title = "Index Path",
        description = """
            The path for the maven index.<br>
            Defaults to '.maven-index/'.
        """
    )
    val indexPath: String = ".maven-index/",
    @get:Doc(
        title = "Incremental Chunks",
        description = """
            Create incremental index chunks.<br>
            Defaults to true.
        """
    )
    val incrementalChunks: Boolean = true, // TODO: 2025-03-12 This is broken
    @Min(min = 1)
    @get:Doc(
        title = "Incremental Chunks Count",
        description = """
            The number of incremental chunks to keep.<br>
            Defaults to 32.
        """
    )
    val incrementalChunksCount: Int = 32,
    @get:Doc(
        title = "Create Checksum Files",
        description = """
            Create checksums for all files (sha1, md5, etc.).<br>
            Defaults to true.
        """
    )
    val createChecksumFiles: Boolean = false,
    @get:Doc(
        title = "Continuous Index Updates",
        description = """
            Continuously updates the index, as new artifacts are uploaded.<br>
            The full scan will still run in the background.<br>
            However, the artifacts indexed by this will not need to be re-indexed.<br>
            Defaults to false.
        """
    )
    val continuousIndexUpdates: Boolean = false, // TODO: 2024-11-06 Implement this
    @Min(min = 1)
    @get:Doc(
        title = "Max Parallel Indexing Repositories",
        description = """
            Maximum number of repositories that can be indexed in parallel.<br>
            This setting only takes effect after a restart.<br>
            Defaults to 1.
        """
    )
    val maxParallelIndexRepositories: Int = 1,
    @get:Doc(
        title = "Indexing Tasks",
        description = """
            List of indexing tasks.
        """
    )
    val indexingTasks: List<IndexingTaskSettings> = listOf(
        IndexingTaskSettings(reference = "default")
    ),
) : SharedSettings {
    public data class IndexingTaskSettings(
        @get:Doc(
            title = "Name",
            description = "The name is not used for anything. This is only for the UI."
        )
        val reference: String = "",
        @get:Doc(
            title = "Enabled",
            description = """
                If this particular indexing task is enabled or not.<br>
                Defaults to true.
            """
        )
        val enabled: Boolean = true,
        @get:Doc(
            title = "Indexing Interval",
            description = """
                How often Reposilite should attempt a full scan to re-index the maven repository.<br>
                With smaller durations the index is updated sooner, but it can significantly increase server load.<br>
                For smaller instances, this should ideally be kept low, but on more powerful servers it can be increased appropriately.<br>
                Defaults to daily.
            """
        )
        val interval: MavenIndexInterval = MavenIndexInterval.DAILY,
        @get:Doc(
            title = "Continuous Updates",
            description = """
                Continuously updates the index, as new artifacts are uploaded.<br>
                The full scan will still run in the background, however the artifacts indexed by this will not need to be re-indexed.<br>
                Defaults to false.
            """
        )
        val continuous: Boolean = false, // TODO: 2025-03-12 Implement this
        // adding @get:Doc() converts it to allOf()
        // https://github.com/dzikoysk/reposilite/issues/1320
        val indexers: EnabledIndexersSettings = EnabledIndexersSettings(),
    ) : SharedSettings {
        private companion object {
            private const val serialVersionUID: Long = 4340096276913889427L
        }
    }

    @Doc("Enabled Indexer Settings", "")
    public data class EnabledIndexersSettings(
        @get:Doc(
            title = "Minimal Indexer",
            description = """
                Indexes a minimal set of things.
            """
        )
        val minimal: Boolean = true,
        @get:Doc(
            title = "Maven Extra Indexer",
            description = """
                Indexes several misc. things, such as sha256, sha512, md5, license, url, etc.<br>
                Will implicitly enable the minimal indexer.
            """
        )
        val mavenExtra: Boolean = false,
        @get:Doc(
            title = "Maven Archetype Indexer",
            description = """
                Indexes maven archetype metadata.<br>
                Will implicitly enable the minimal indexer.
            """
        )
        val mavenArchetype: Boolean = false,
        @get:Doc(
            title = "Maven Plugin Indexer",
            description = """
                Indexes maven plugin metadata.<br>
                Will implicitly enable the minimal indexer.
            """
        )
        val mavenPlugin: Boolean = false,
        @get:Doc(
            title = "OSGI Metadata Indexer",
            description = """
                Indexes OSGI Metadata.
            """
        )
        val osgiMetadata: Boolean = false,
        @get:Doc(
            title = "Jar Contents Indexer",
            description = """
                Indexes all the classnames within a jar.<br>
                Will implicitly enable the minimal indexer.
            """
        )
        val jarContent: Boolean = false,
    ) : SharedSettings {
        private companion object {
            private const val serialVersionUID: Long = -122169967566568022L
        }
    }

    public enum class MavenIndexInterval(public val duration: Duration) {
        TWICE_HOURLY(30.minutes),
        HOURLY(1.hours),
        BI_HOURLY(2.hours),
        DAILY(1.days),
        WEEKLY(7.days),
        MONTHLY(30.days),
    }

    private companion object {
        private const val serialVersionUID: Long = -5188044794320568099L
    }
}
