package com.jib.app.ui.station

import androidx.lifecycle.SavedStateHandle
import com.jib.app.data.model.Station
import com.jib.app.data.repository.StationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StationDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun handle(id: String = "abc") = SavedStateHandle(mapOf("stationId" to id))

    @Test
    fun `onCleared cancels the active collectJob`() = runTest(testDispatcher) {
        // Repo whose Flow never completes — simulates an in-flight network/cache stream.
        val hangingRepo = object : StationRepository {
            override fun getStations(
                swLat: Double, swLng: Double, neLat: Double, neLng: Double,
                connectorType: String?,
            ): Flow<List<Station>> = flow { delay(Long.MAX_VALUE) }

            override fun getStation(id: String): Flow<Station?> =
                flow { delay(Long.MAX_VALUE) }

            override suspend fun refreshStation(id: String) {
                delay(Long.MAX_VALUE)
            }
        }

        val viewModel = StationDetailViewModel(hangingRepo, handle())
        advanceUntilIdle()

        val job = viewModel.collectJob
        assertNotNull("collectJob should be non-null after init", job)

        viewModel.onCleared()

        assertTrue("collectJob must be cancelled after onCleared()", job!!.isCancelled)
    }

    @Test
    fun `network error surfaces a retryable errorMessage while cache still serves`() =
        runTest(testDispatcher) {
            val cached = Station(
                id = "abc", name = "Cached", latitude = 1.0, longitude = 2.0,
                connectorTypes = "[\"CCS\"]", powerKw = 50.0, networkOperator = null,
                isAvailable = true, address = null,
            )
            val repo = object : StationRepository {
                override fun getStations(
                    swLat: Double, swLng: Double, neLat: Double, neLng: Double,
                    connectorType: String?,
                ): Flow<List<Station>> = flow { }

                override fun getStation(id: String): Flow<Station?> =
                    flow { emit(cached) }

                override suspend fun refreshStation(id: String) {
                    throw IllegalStateException("offline")
                }
            }

            val viewModel = StationDetailViewModel(repo, handle())
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(cached, state.station)
            assertEquals("offline", state.errorMessage)
        }
}
