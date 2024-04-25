package com.tdcolvin.bleclient.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

val CTF_SERVICE_UUID: UUID = UUID.fromString("E20A39F4-73F5-4BC4-A12F-17D1AD07A961")
val READ_CHARACTERISTIC_UUID: UUID = UUID.fromString("8c380001-10bd-4fdb-ba21-1922d6cf860d")
val WRITE_CHARACTERISTIC_UUID: UUID = UUID.fromString("08590F7E-DB05-467E-8757-72F6FAEB13D4")

@Suppress("DEPRECATION")
class BLEDeviceConnection @RequiresPermission("PERMISSION_BLUETOOTH_CONNECT") constructor(
    private val context: Context,
    private val bluetoothDevice: BluetoothDevice
) {
    val isConnected = MutableStateFlow(false)
    val passwordRead = MutableStateFlow<String?>(null)
    val successfulNameWrites = MutableStateFlow(0)
    val services = MutableStateFlow<List<BluetoothGattService>>(emptyList())
    val jsonString = """
        {
            "presentation": {
                "type": "verifiablePresentation",
                "id": "did:waff:W6hLpTWEbsUW/0Hs6NglWF3g",
                "credential": {
                    "type": "verifiableCredential",
                    "issuer": {
                        "name": "한양대학교",
                        "id": "did:waff:TCSw+75WvYTptwNP8q5GxSjQ"
                    },
                    "issuanceDate": "1705900000",
                    "expirationDate": "1706900000",
                    "credentialSubjects": {
                        "id": "did:waff:W6hLpTWEbsUW/0Hs6NglWF3g",
                        "name": "전효진",
                        "subjects": [{
                            "document": {
                                "name": "학생증",
                                "contents": [
                                    { "key": "이름", "value": "전효진" },
                                    { "key": "학번", "value": "2018380355" },
                                    { "key": "학과", "value": "컴퓨터소프트웨어학과" },
                                    { "key": "입학년월", "value": "2018.03" }
                                ]
                            }
                        }]
                    },
                    "proof": {
                        "signatureAlgorithm": "secp256k1",
                        "created": "1705900000",
                        "creatorID": "did:waff:TCSw+75WvYTptwNP8q5GxSjQ",
                        "jws": "MEUCIQCKWDIAJQbnt/t42k0NHfJu6xpEX5QwDbNaIUBgPT1oCgIgE9rZQqPRW+uIjkXltzbMOfZqib43IxKMCmJ0WjDTXOo="
                    },
                    "verifier": {
                        "name": "김현아",
                        "id": "did:waff:Xz02rvh0jnQMa0IQEywY0LSQ"
                    }
                },
                "proof": {
                    "signatureAlgorithm": "secp256k1",
                    "created": "1706000000",
                    "creatorID": "did:waff:W6hLpTWEbsUW/0Hs6NglWF3g",
                    "jws": "MEQCIBrDHgn7j+XQkQZom2NywbA/aNJxswk2zjwb/7eMrYEaAiBjN45eLYO7jx69IaceDzhTWEF+kx//URLDY/GAnEmvvA=="
                }
                
            },

              "vc_certificaiton ": {
                "certificationName": "한양대학교의 인증서",
                "signatureAlgorithm": "secp256k1",
                "id": "did:waff:TCSw+75WvYTptwNP8q5GxSjQ",
                "name": "한양대학교",
                "pubKey": "-----BEGIN PUBLIC KEY-----\nMFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEomGvR5L0DkjzBoqVs8ObPXoJYERnn/Ktmjpd0Dcc9LxUd4aCnHVB5UuRV4xDqUTCSw+75WvYTptwNP8q5GxSjQ==\n-----END PUBLIC KEY-----",
                "created": "1705923040"
            },

            "vp_certification": {
                "certificationName": "전효진의 인증서",
                "signatureAlgorithm": "secp256k1",
                "id": "did:waff:W6hLpTWEbsUW/0Hs6NglWF3g",
                "name": "전효진",
                "pubKey": "-----BEGIN PUBLIC KEY-----\nMFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAESnJ+xVVzWWs0zIJiUJEsPvvnZFBLdCRPAo1eNcP0ouE5gQIhL1Q/ykhLSQHozSW6hLpTWEbsUW/0Hs6NglWF3g==\n-----END PUBLIC KEY-----",
                "created": "1705923050"
            }
        }
""".trimIndent().replace(" ", "").replace("\n", "")
    private val textList = divideText(jsonString)
    private var index = 0


    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val connected = newState == BluetoothGatt.STATE_CONNECTED
            if (connected) {
                //read the list of services
                services.value = gatt.services
            }
            isConnected.value = connected
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            services.value = gatt.services
            try {
                gatt.requestMtu(512)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (characteristic.uuid == READ_CHARACTERISTIC_UUID) {
                Log.d("BLEClient", String(characteristic.value))
                passwordRead.value = String(characteristic.value)
            }
        }

        @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (characteristic.uuid == WRITE_CHARACTERISTIC_UUID) {
                successfulNameWrites.update { it + 1 }
                if(index < textList.size - 1) {
                    index++
                    writeName(index)
                }
            }
        }
    }

    private var gatt: BluetoothGatt? = null

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    fun connect() {
        gatt = bluetoothDevice.connectGatt(context, false, callback)
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    fun discoverServices() {
        gatt?.discoverServices()
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    fun readPassword() {
        val service = gatt?.getService(CTF_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(READ_CHARACTERISTIC_UUID)
        if (characteristic != null) {
            val success = gatt?.readCharacteristic(characteristic)
            Log.v("bluetooth", "Read status: $success")
        }
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    fun writeName(index: Int) = CoroutineScope(Dispatchers.Default).launch {
        val service = gatt?.getService(CTF_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(WRITE_CHARACTERISTIC_UUID)
        if (characteristic != null) {
            Log.v("bluetooth", "Write Start")
            val text = textList[index]
            characteristic.value = text.toByteArray()
            val success = if (Build.VERSION.SDK_INT >= 33) {
                gatt?.writeCharacteristic(
                    characteristic,
                    text.toByteArray(),
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                )
            } else {
                gatt?.writeCharacteristic(characteristic)
            }
            Log.v("bluetooth", "Write status: $success")
            Log.v("bluetooth", "Write End")
        }
    }

    private fun divideText(text: String): List<String> {
        val list = text.chunked(256)
        return list.mapIndexed { index, str ->
            return@mapIndexed if (index == list.size - 1) "$index/${str}/EOM"
            else "$index/$str"
        }
    }
}