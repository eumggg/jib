package com.jib.app.ui.map

import com.jib.app.data.model.Station
import com.jib.app.data.repository.StationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val hangingRepo = object : StationRepository {
        override fun getStations(
            swLat: Double, swLng: Double, neLat: Double, neLng: Double,
            connectorType: String?,
        ): Flow<List<Station>> = flow { delay(Long.MAX_VALUE) }

        override fun getStation(id: String): Flow<Station?> = flowOf(null)
        override suspend fun refreshStation(id: String) = Unit
    }

    private class RecordingRepo : StationRepository {
        val seenConnectorTypes = mutableListOf<String?>()
        override fun getStations(
            swLat: Double, swLng: Double, neLat: Double, neLng: Double,
            connectorType: String?,
        ): Flow<List<Station>> {
            seenConnectorTypes += connectorType
            return flowOf(emptyList())
        }
        override fun getStation(id: String): Flow<Station?> = flowOf(null)
        override suspend fun refreshStation(id: String) = Unit
    }

    @Test
    fun `onCleared cancels the active fetchJob`() = runTest(testDispatcher) {
        val viewModel = MapViewModel(hangingRepo)
        viewModel.fetchStations(0.0, 0.0, 1.0, 1.0)
        advanceUntilIdle()

        val job = viewModel.fetchJob
        assertNotNull("fetchJob should be non-null after fetchStations()", job)

        viewModel.onCleared()

        assertTrue("fetchJob must be cancelled after onCleared()", job!!.isCancelled)
    }

    @Test
    fun `setConnectorFilter persists across pan and forwards wire value`() = runTest(testDispatcher) {
        val repo = RecordingRepo()
        val viewModel = MapViewModel(repo)

        // Initial viewport fetch (no filter).
        viewModel.fetchStations(0.0, 0.0, 1.0, 1.0)
        advanceUntilIdle()

        // Apply CCS filter — should refetch immediately against the same bounds.
        viewModel.setConnectorFilter(ConnectorType.CCS)
        advanceUntilIdle()

        // Simulate a pan — fetchStations is called again from the camera-idle effect.
        viewModel.fetchStations(0.5, 0.5, 1.5, 1.5)
        advanceUntilIdle()

        assertEquals(listOf(null, "CCS", "CCS"), repo.seenConnectorTypes)
        assertEquals(ConnectorType.CCS, viewModel.uiState.value.selectedConnector)
    }

    @Test
    fun `setConnectorFilter to null clears filter`() = runTest(testDispatcher) {
        val repo = RecordingRepo()
        val viewModel = MapViewModel(repo)
        viewModel.fetchStations(0.0, 0.0, 1.0, 1.0)
        advanceUntilIdle()
        viewModel.setConnectorFilter(ConnectorType.TESLA)
        advanceUntilIdle()
        viewModel.setConnectorFilter(null)
        advanceUntilIdle()

        assertEquals(listOf(null, "Tesla", null), repo.seenConnectorTypes)
        assertNull(viewModel.uiState.value.selectedConnector)
    }
}
