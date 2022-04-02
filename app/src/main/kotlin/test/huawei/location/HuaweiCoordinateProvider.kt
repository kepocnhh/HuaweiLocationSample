package test.huawei.location

import android.content.Context
import android.location.Location
import android.os.Looper
import com.huawei.hms.location.FusedLocationProviderClient
import com.huawei.hms.location.LocationAvailability
import com.huawei.hms.location.LocationCallback
import com.huawei.hms.location.LocationRequest
import com.huawei.hms.location.LocationResult
import com.huawei.hms.location.LocationServices
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HuaweiCoordinateProvider(
    private val context: Context
) : CoordinateProvider {
    private fun getProviderClient(): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(context) ?: error(
            "Huawei FusedLocationProviderClient null!"
        )
    }

    private suspend fun FusedLocationProviderClient.getCurrentLocation(): Location {
        return suspendCancellableCoroutine<Location> { continuation ->
            val callback = object : LocationCallback() {
                private val rc = LocationCallback()
                override fun onLocationResult(result: LocationResult) {
                    println("on location result:")
                    removeLocationUpdates(rc)
                    val location = result.locations.firstOrNull()
                    if (location == null) {
                        continuation.resumeWithException(NullPointerException())
                    } else {
                        continuation.resume(location)
                    }
                }

                override fun onLocationAvailability(availability: LocationAvailability?) {
                    println("on location availability:")
                    removeLocationUpdates(rc)
                    if (availability == null) {
                        continuation.resumeWithException(NullPointerException())
                    } else {
                        if (!availability.isLocationAvailable) {
                            continuation.resumeWithException(
                                IllegalStateException(
                                    """
                                        Location is not available!
                                         - location: ${availability.locationStatus}
                                         - cell: ${availability.cellStatus}
                                         - wifi: ${availability.wifiStatus}
                                    """.trimIndent()
                                )
                            )
                        }
                    }
                }
            }
            val request = LocationRequest()
            request.interval = 1_000
            request.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener {
                    continuation.resumeWithException(it)
                }.result
        }
    }

    private fun FusedLocationProviderClient.getLocation(): Location {
        return runBlocking {
            withTimeout(30_000) {
                getCurrentLocation()
            }
        }
    }

    override fun getCoordinate(): Coordinate {
        val result = getProviderClient().getLocation()
        return Coordinate(
            latitude = result.latitude,
            longitude = result.longitude
        )
    }
}
