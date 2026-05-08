package com.jib.app.ui.submit

import com.jib.app.data.model.Station
import com.jib.app.data.repository.StationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubmitStationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeRepo(
        private val onSubmit: (
            String, String, Double, Double, List<String>, Double?, String?,
        ) -> Result<Station>,
    ) : StationRepository {
        var lastIdempotencyKey: String? = null
        var submitCalls = 0
        override fun getStations(
            swLat: Double, swLng: Double, neLat: Double, neLng: Double, connectorType: String?,
        ): Flow<List<Station>> = flowOf(emptyList())

        override fun getStation(id: String): Flow<Station?> = flowOf(null)
        override suspend fun refreshStation(id: String) = Unit

        override suspend fun submitStation(
            idempotencyKey: String,
            name: String,
            latitude: Double,
            longitude: Double,
            connectorTypes: List<String>,
            powerKw: Double?,
            networkOperator: String?,
        ): Result<Station> {
            submitCalls++
            lastIdempotencyKey = idempotencyKey
            return onSubmit(idempotencyKey, name, latitude, longitude, connectorTypes, powerKw, networkOperator)
        }
    }

    private fun station(id: String = "srv-1") = Station(
        id = id, name = "Test", latitude = 1.0, longitude = 2.0,
        connectorTypes = "[]", powerKw = null, networkOperator = null, isAvailable = true,
    )

    @Test
    fun `advance from Location requires lat-lng — flips locationError otherwise`() = runTest(testDispatcher) {
        val vm = SubmitStationViewModel(FakeRepo { _, _, _, _, _, _, _ -> Result.success(station()) })

        assertFalse(vm.advance())
        assertTrue(vm.uiState.value.locationError)
        assertEquals(SubmitStep.Location, vm.uiState.value.step)

        vm.setLocation(37.7749, -122.4194)
        assertTrue(vm.advance())
        assertEquals(SubmitStep.Details, vm.uiState.value.step)
    }

    @Test
    fun `advance from Details requires non-blank name`() = runTest(testDispatcher) {
        val vm = SubmitStationViewModel(FakeRepo { _, _, _, _, _, _, _ -> Result.success(station()) })
        vm.setLocation(1.0, 2.0)
        vm.advance() // -> Details

        assertFalse(vm.advance())
        assertTrue(vm.uiState.value.nameError)

        vm.setName("Joe's Charger")
        assertTrue(vm.advance())
        assertEquals(SubmitStep.Review, vm.uiState.value.step)
    }

    @Test
    fun `submit forwards form fields and reports success`() = runTest(testDispatcher) {
        var captured: List<Any?>? = null
        val repo = FakeRepo { key, name, lat, lng, conns, kw, net ->
            captured = listOf(key, name, lat, lng, conns, kw, net)
            Result.success(station(id = "srv-99"))
        }
        val vm = SubmitStationViewModel(repo)

        vm.setLocation(40.0, -74.0)
        vm.setName("Times Square Charger")
        vm.toggleConnector("CCS")
        vm.toggleConnector("Tesla")
        vm.toggleConnector("Tesla") // toggle off
        vm.setPowerKw("150")
        vm.setNetworkOperator("ChargePoint")

        vm.submit()
        advanceUntilIdle()

        val (key, name, lat, lng, conns, kw, net) = captured!!.let {
            @Suppress("UNCHECKED_CAST")
            Septuple(
                it[0] as String, it[1] as String, it[2] as Double, it[3] as Double,
                it[4] as List<String>, it[5] as Double?, it[6] as String?,
            )
        }

        assertNotNull(key)
        assertEquals("Times Square Charger", name)
        assertEquals(40.0, lat, 0.0)
        assertEquals(-74.0, lng, 0.0)
        assertEquals(listOf("CCS"), conns)
        assertEquals(150.0, kw!!, 0.0)
        assertEquals("ChargePoint", net)

        val res = vm.uiState.value.result
        assertTrue("Expected Success, got $res", res is SubmitResult.Success)
        assertEquals("srv-99", (res as SubmitResult.Success).stationId)
    }

    @Test
    fun `retry reuses the same idempotency key`() = runTest(testDispatcher) {
        var attempt = 0
        val repo = FakeRepo { _, _, _, _, _, _, _ ->
            if (++attempt == 1) Result.failure(RuntimeException("network down"))
            else Result.success(station(id = "srv-200"))
        }
        val vm = SubmitStationViewModel(repo)
        vm.setLocation(0.0, 0.0)
        vm.setName("Any Charger")

        val key0 = vm.uiState.value.idempotencyKey

        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.result is SubmitResult.Error)
        val keyAfterFailure = repo.lastIdempotencyKey

        vm.retry()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.result is SubmitResult.Success)
        assertEquals(2, repo.submitCalls)
        // Same key sent on both attempts so backend dedups (idempotency contract).
        assertEquals(key0, keyAfterFailure)
        assertEquals(key0, repo.lastIdempotencyKey)
    }

    @Test
    fun `submit with missing location bounces back to Location step`() = runTest(testDispatcher) {
        val vm = SubmitStationViewModel(FakeRepo { _, _, _, _, _, _, _ -> Result.success(station()) })
        vm.setName("Anything")
        vm.submit()
        advanceUntilIdle()
        assertEquals(SubmitStep.Location, vm.uiState.value.step)
        assertTrue(vm.uiState.value.locationError)
        assertNull(vm.uiState.value.latitude)
    }

    private data class Septuple<A, B, C, D, E, F, G>(
        val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G,
    )
}
