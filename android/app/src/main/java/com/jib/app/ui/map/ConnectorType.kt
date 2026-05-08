package com.jib.app.ui.map

/**
 * The connector types the filter UI exposes. The wire value is what the
 * backend `/api/stations?connectorType=` query expects, which is the same
 * string stored inside Station.connectorTypes (a JSON-encoded list).
 */
enum class ConnectorType(val wireValue: String, val displayName: String) {
    CCS("CCS", "CCS"),
    CHADEMO("CHAdeMO", "CHAdeMO"),
    J1772("J1772", "J1772"),
    TESLA("Tesla", "Tesla"),
}
