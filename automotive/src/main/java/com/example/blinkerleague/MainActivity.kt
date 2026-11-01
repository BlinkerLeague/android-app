package com.example.blinkerleague

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue

class MainActivity : AppCompatActivity() {
    private val TAG = "BlinkerLeague"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "MainActivity started")

        // Initialize Pleos Vehicle SDK
        val vehicle = Vehicle(this)
        vehicle.initialize()

        // Get door position
        val door = vehicle.getDoor()
        door.getCurrentDoorPosition(
            area = DoorArea(DoorValue.ROW_1_LEFT),
            onComplete = { position: Int? ->
                Log.d(TAG, "Door position retrieved successfully: $position")
            },
            onFailed = { e: Exception ->
                Log.e(TAG, "Failed to get door position: ${e.message}", e)
            }
        )
    }
}
