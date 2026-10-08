package dev.cniekirk.wikidroid.core.common

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class ResultTest {
    private val success: Result<Int, DataError> = Result.Success(2)
    private val failure: Result<Int, DataError> = Result.Failure(DataError.NotFound)

    @Test
    fun map_transformsSuccessOnly() {
        assertThat(success.map { it * 10 }).isEqualTo(Result.Success(20))
        assertThat(failure.map { it * 10 }).isEqualTo(Result.Failure(DataError.NotFound))
    }

    @Test
    fun mapError_transformsFailureOnly() {
        assertThat(failure.mapError { "e" }).isEqualTo(Result.Failure("e"))
        assertThat(success.mapError { "e" }).isEqualTo(Result.Success(2))
    }

    @Test
    fun flatMap_shortCircuitsOnFailure() {
        assertThat(success.flatMap { Result.Success(it + 1) }).isEqualTo(Result.Success(3))
        assertThat(failure.flatMap { Result.Success(it + 1) }).isEqualTo(failure)
    }

    @Test
    fun fold_picksMatchingBranch() {
        assertThat(success.fold({ "ok $it" }, { "err" })).isEqualTo("ok 2")
        assertThat(failure.fold({ "ok $it" }, { "err" })).isEqualTo("err")
    }

    @Test
    fun accessors_exposeValueOrError() {
        assertThat(success.getOrNull()).isEqualTo(2)
        assertThat(success.errorOrNull()).isNull()
        assertThat(failure.getOrNull()).isNull()
        assertThat(failure.errorOrNull()).isEqualTo(DataError.NotFound)
        assertThat(success.isSuccess).isTrue()
        assertThat(failure.isSuccess).isFalse()
    }

    @Test
    fun onSuccessAndOnFailure_runOnlyForMatchingCase() {
        val seen = mutableListOf<String>()

        success.onSuccess { seen += "s$it" }.onFailure { seen += "f" }
        failure.onSuccess { seen += "s$it" }.onFailure { seen += "f" }

        assertThat(seen).containsExactly("s2", "f").inOrder()
    }

    @Test
    fun dataResultOf_returnsValue() {
        assertThat(dataResultOf { 7 }).isEqualTo(Result.Success(7))
    }

    @Test
    fun dataResultOf_mapsIoExceptionToNetworkError() {
        val result = dataResultOf<Int> { throw IOException("offline") }

        assertThat(result.errorOrNull()).isEqualTo(DataError.Network())
    }

    @Test
    fun dataResultOf_wrapsOtherExceptionsAsUnknown() {
        val boom = IllegalStateException("boom")

        val result = dataResultOf<Int> { throw boom }

        assertThat(result.errorOrNull()).isEqualTo(DataError.Unknown(boom))
    }

    @Test
    fun dataResultOf_rethrowsCancellation() {
        assertThrows(CancellationException::class.java) {
            dataResultOf<Int> { throw CancellationException("cancelled") }
        }
    }
}
