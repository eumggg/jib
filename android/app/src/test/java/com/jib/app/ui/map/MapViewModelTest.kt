package com.jib.app.ui.map

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // viewModelScope uses Dispatchers.Main; redirect to the test dispatcher.
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val hangingRepo = object : StationRepository {
        override fun getStations(
            swLat: Double, swLng: Double, neLat: Double, neLng: Double,
        ): Flow<List<Station>> = flow { delay(Long.MAX_VALUE) }
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
}
