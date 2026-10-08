package dev.cniekirk.wikidroid.core.data

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.common.DataError
import dev.cniekirk.wikidroid.core.common.Result
import dev.cniekirk.wikidroid.core.common.getOrNull
import dev.cniekirk.wikidroid.core.model.Edition
import dev.cniekirk.wikidroid.core.model.LatestVersions
import dev.cniekirk.wikidroid.core.model.ThemeMode
import dev.cniekirk.wikidroid.core.model.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WikiInfoRepositoryTest {
    private val remote = FakeRemote()
    private val repository = WikiInfoRepositoryImpl(remote)

    @Test
    fun `latest versions are passed through`() =
        runTest {
            val versions = LatestVersions(java = "1.21.5", javaSnapshot = null, bedrock = null, bedrockPreview = null)
            remote.versionsResult = Result.Success(versions)

            assertThat(repository.latestVersions()).isEqualTo(Result.Success(versions))
        }

    @Test
    fun `random skips version and snapshot pages`() =
        runTest {
            remote.randomBatches +=
                Result.Success(
                    listOf(page("Java Edition 1.21.5"), page("24w14a"), page("Villager", extract = "A mob.")),
                )

            val summary = repository.randomArticle().getOrNull()!!

            assertThat(summary.title).isEqualTo("Villager")
            assertThat(summary.description).isEqualTo("A mob.")
            assertThat(remote.calls).containsExactly("random:5")
        }

    @Test
    fun `random asks again when a whole batch is version pages`() =
        runTest {
            remote.randomBatches += Result.Success(listOf(page("Java Edition 1.20"), page("Bedrock Edition 1.20.0")))
            remote.randomBatches += Result.Success(listOf(page("Zombie")))

            assertThat(repository.randomArticle().getOrNull()!!.title).isEqualTo("Zombie")
            assertThat(remote.calls).hasSize(2)
        }

    @Test
    fun `random gives up as not found after a few all-version batches`() =
        runTest {
            repeat(5) { remote.randomBatches += Result.Success(listOf(page("Java Edition 1.20"))) }

            assertThat(repository.randomArticle()).isEqualTo(Result.Failure(DataError.NotFound))
            assertThat(remote.calls).hasSize(3)
        }

    @Test
    fun `random does not retry a transport failure`() =
        runTest {
            remote.randomBatches += Result.Failure(DataError.Network())

            assertThat(repository.randomArticle()).isEqualTo(Result.Failure(DataError.Network()))
            assertThat(remote.calls).hasSize(1)
        }

    @Test
    fun `version pages are recognised by title`() {
        listOf(
            "Java Edition 1.21.5",
            "Java Edition Beta 1.8",
            "Java Edition Infdev 20100618",
            "Bedrock Edition 1.21.70",
            "Pocket Edition Alpha 0.1.0",
            "24w14a",
            "Java Edition 1.20.5-pre1",
            "Bedrock Edition version history",
            "Java Edition 1.21.5 Release Candidate 1",
        ).forEach { assertThat(it.looksLikeVersionPage()).isTrue() }
        listOf(
            "Creeper",
            "Java Edition",
            "Bedrock Edition",
            "Diamond",
            "Tutorial:Mining",
            "Edition",
        ).forEach { assertThat(it.looksLikeVersionPage()).isFalse() }
    }
}

class SettingsRepositoryTest {
    private val repository = DataStoreSettingsRepository(FakePreferencesDataSource())

    @Test
    fun `each setter changes only its own preference`() =
        runTest {
            repository.setThemeMode(ThemeMode.Dark)
            repository.setDynamicColor(false)
            repository.setTextScale(1.25f)
            repository.setPreferredEdition(Edition.Bedrock)
            repository.setSaveHistory(false)

            assertThat(repository.preferences.first())
                .isEqualTo(
                    UserPreferences(
                        themeMode = ThemeMode.Dark,
                        dynamicColor = false,
                        textScale = 1.25f,
                        preferredEdition = Edition.Bedrock,
                        saveHistory = false,
                    ),
                )
        }
}
