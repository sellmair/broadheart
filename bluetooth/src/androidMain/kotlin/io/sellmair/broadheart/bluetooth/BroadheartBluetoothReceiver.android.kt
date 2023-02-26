@file:OptIn(FlowPreview::class)

package io.sellmair.broadheart.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import io.sellmair.broadheart.bluetooth.ServiceConstants.userIdCharacteristicUuidString
import io.sellmair.broadheart.bluetooth.ServiceConstants.userNameCharacteristicUuidString
import io.sellmair.broadheart.model.HeartRateMeasurement
import io.sellmair.broadheart.model.User
import io.sellmair.broadheart.model.UserId
import io.sellmair.broadheart.utils.distinct
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import java.util.*
import kotlin.coroutines.CoroutineContext

suspend fun BroadheartBluetoothReceiver(
    context: Context, scope: CoroutineScope
): BroadheartBluetoothReceiver {
    /* Wait for bluetooth permission */
    while (
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    ) {
        delay(1000)
    }

    val receivedUsers = MutableSharedFlow<User>()
    val receivedHeartRateMeasurements = MutableSharedFlow<HeartRateMeasurement>()

    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    manager.scanForBroadheartPeripherals()
        .distinct { device -> device.address }
        .map { device -> device.connect(context, currentCoroutineContext().job) }
        .collect { connectedDevice ->
            scope.launch {
                val user = connectedDevice.user().stateIn(scope)
                scope.launch {
                    user.collect { user ->
                        println(user)
                    }
                }
            }
        }

    return object : BroadheartBluetoothReceiver {
        override val receivedUsers: SharedFlow<User> =
            receivedUsers.asSharedFlow()

        override val receivedHeartRateMeasurements: SharedFlow<HeartRateMeasurement> =
            receivedHeartRateMeasurements.asSharedFlow()
    }
}


@RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
private fun BluetoothManager.scanForBroadheartPeripherals(): Flow<BluetoothDevice> = callbackFlow {
    val scanCallback = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.d("bluetooth", "onScanFailed(errorCode=$errorCode)")
        }

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (result == null) return
            trySend(result.device)
        }
    }

    val scanFilter = ScanFilter.Builder()
        .setServiceUuid(ParcelUuid(UUID.fromString(ServiceConstants.serviceUuidString)))
        .build()

    val scanSettings = ScanSettings.Builder()
        .setLegacy(false)
        .build()

    adapter.bluetoothLeScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
    awaitClose { adapter.bluetoothLeScanner.stopScan(scanCallback) }
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
private fun BluetoothDevice.connect(context: Context, parentJob: Job): ConnectedBluetoothDevice {
    val connectedBluetoothDevice = ConnectedBluetoothDevice(parentJob)
    connectGatt(context, true, connectedBluetoothDevice, BluetoothDevice.TRANSPORT_LE)
    return connectedBluetoothDevice
}

@SuppressLint("MissingPermission")
private class ConnectedBluetoothDevice(
    private val parentJob: Job?
) : BluetoothGattCallback() {

    private var connectedCoroutinesScope: CoroutineScope? = null
    private var userId = MutableStateFlow<Long?>(null)
    private var userName = MutableStateFlow<String?>(null)

    private val onCharacteristicReadChannel = Channel<CharacteristicReadResult>()

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        connectedCoroutinesScope?.cancel()
        Log.d("bluetooth", "onConnectionStateChange(status=$status, newState=$newState")
        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            parentJob?.invokeOnCompletion { gatt.close() }
            gatt.discoverServices()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        super.onServicesDiscovered(gatt, status)
        Log.d("bluetooth", "onServicesDiscovered(status=$status)")
        connectedCoroutinesScope = object : CoroutineScope {
            override val coroutineContext: CoroutineContext =
                Dispatchers.Main + Job(parentJob)
        }

        connectedCoroutinesScope?.launch {
            val service = gatt.getService(UUID.fromString(ServiceConstants.serviceUuidString)) ?: return@launch

            val userIdCharacteristic = service.getCharacteristic(UUID.fromString(userIdCharacteristicUuidString))
                ?: return@launch

            val userNameCharacteristic = service.getCharacteristic(UUID.fromString(userNameCharacteristicUuidString))
                ?: return@launch

            userId.value = ByteBuffer.wrap(gatt.readValue(userIdCharacteristic)).getLong()
            userName.value = gatt.readValue(userNameCharacteristic).decodeToString()
        }
    }

    private suspend fun BluetoothGatt.readValue(characteristic: BluetoothGattCharacteristic): ByteArray {
        val read = readCharacteristic(characteristic)
        println("read=$read")
        return onCharacteristicReadChannel.receiveAsFlow()
            .filterIsInstance<CharacteristicReadResult.Success>()
            .filter { it.characteristic == characteristic }
            .first().value
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int
    ) {
        super.onCharacteristicRead(gatt, characteristic, value, status)
        Log.d("bluetooth", "onCharacteristicRead(status=$status)")
        connectedCoroutinesScope?.launch {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onCharacteristicReadChannel.trySend(CharacteristicReadResult.Success(characteristic, value))
            } else {
                onCharacteristicReadChannel.trySend(CharacteristicReadResult.Failed(characteristic, status))
            }
        }
    }

    fun user(): Flow<User> {
        return combine(userId.filterNotNull(), userName.filterNotNull()) { id, name ->
            User(
                isMe = false,
                id = UserId(id),
                name = name
            )
        }
    }
}

sealed interface CharacteristicReadResult {
    val characteristic: BluetoothGattCharacteristic

    class Failed(
        override val characteristic: BluetoothGattCharacteristic,
        val status: Int
    ) : CharacteristicReadResult

    class Success(
        override val characteristic: BluetoothGattCharacteristic,
        val value: ByteArray
    ) : CharacteristicReadResult
}
