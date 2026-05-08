package io.lpin.android.sdk.plac.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.lpin.android.sdk.plac.scanner.ble.advertising.IBeacon
import io.lpin.android.sdk.plac.scanner.ble.advertising.PayloadParser


@SuppressLint("MissingPermission")
class BeaconScannerImpl(
        val context: Context,
        private val params: ScannerParams
) : BeaconScanner {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    /** 에러 코드 **/
    private var errorCode = 0

    /** 비콘 데이터 **/
    private val beaconScanResults: ArrayList<BeaconData> = arrayListOf()

    /** 비콘 데이터 수집중 **/
    private var isScanInProgress = false

    /** 비콘 데이터 콜백 **/
    private val scanCallBack: BleScanCallback = BleScanCallback()

    @Throws(ScannerException::class)
    override fun init() {
        var permission: Array<String> = ANDROID_12_BLE_PERMISSIONS

        // SDK31대응
        if (Build.VERSION.SDK_INT < Scanner.OS_VERSION_S) {
            permission = BLE_PERMISSIONS
        }
        permission.forEach {
            Log.d("permission", it)
        }
        // 권한 여부
        if (!ScannerValidate.hasPermission(context, permission)) {
            throw ScannerException(ScannerType.BEACON, ScannerException.Type.PERMISSION_DENIED)
        }
        // 블루투스 모듈 초기화
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
//        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            throw ScannerException(ScannerType.BEACON, ScannerException.Type.DISABLED);
        }
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            throw ScannerException(ScannerType.BEACON, ScannerException.Type.UNKNOWN_ERROR)
        }
    }

    override fun startScanning() {
        if (isScanInProgress) {
            throw ScannerException(ScannerType.BEACON, ScannerException.Type.SCAN_ALREADY_IN_PROGRESS);
        }
        // 데이터 초기화
        errorCode = 0
        synchronized(beaconScanResults) {
            beaconScanResults.clear()
        }

        if (bluetoothLeScanner == null) {
            throw ScannerException(ScannerType.BEACON, ScannerException.Type.UNKNOWN_ERROR)
        }

        isScanInProgress = try {
            val builder = ScanSettings.Builder()
            builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            builder.setReportDelay(0)
            bluetoothLeScanner?.startScan(null, builder.build(), scanCallBack)
            true
        } catch (e: Exception) {
            throw ScannerException(ScannerType.BEACON, ScannerException.Type.UNKNOWN_ERROR)
        }
    }


    override fun stopScanning() {
        bluetoothLeScanner?.flushPendingScanResults(scanCallBack)
        bluetoothLeScanner?.stopScan(scanCallBack)
        // 콜백 대기
        waitForMainLooper(params.bluetoothFlushResultsTimeoutMs)
        isScanInProgress = false
    }

    private fun waitForMainLooper(maxWaitTimeoutMs: Long) {
        try {
            val scanLock = Object()
            synchronized(scanLock) {
                val handler = Handler(Looper.getMainLooper())
                handler.post {
                    try {
                        synchronized(scanLock) {
                            scanLock.notify()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception waiting for main looper")
                        e.printStackTrace()
                    }
                }
                scanLock.wait(maxWaitTimeoutMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception waiting for main looper")
            e.printStackTrace()
        }
    }

    override fun getErrorCode(): Int {
        return errorCode
    }

    override fun getScanResults(): List<BeaconData> {
        var output: MutableList<BeaconData>
        synchronized(beaconScanResults) {
            val maxScanResultSize = params.bluetoothMaxScanResults

            // 데이터 중복값 제거
            val tmpOutput =
                    beaconScanResults
                            .groupBy { it.bssid }
                            .map { scanGroup ->
                                scanGroup.value.sortedBy { -it.rssi }.first()
                            }
            if (tmpOutput.size > maxScanResultSize) {
                output = ArrayList(maxScanResultSize)
                output.addAll(tmpOutput.subList(0, maxScanResultSize))
            } else {
                output = ArrayList(tmpOutput.size)
                output.addAll(tmpOutput)
            }
        }
        return output
    }

    /** Bluetooth Le 데이터 결과 **/
    private inner class BleScanCallback : ScanCallback() {

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            this@BeaconScannerImpl.errorCode = errorCode
        }

        override fun onBatchScanResults(scanResults: List<ScanResult>) {
            super.onBatchScanResults(scanResults)
            try {
                synchronized(beaconScanResults) {
                    for (scanResult in scanResults) {
                        scanResult.toIBeaconData()?.apply {
                            beaconScanResults.add(this)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in ble scan callback")
                e.printStackTrace()
            }
        }

        override fun onScanResult(callbackType: Int, scanResult: ScanResult) {
            super.onScanResult(callbackType, scanResult)
            try {
                synchronized(beaconScanResults) {
                    scanResult.toIBeaconData()?.apply {
                        beaconScanResults.add(this)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in ble scan callback")
                e.printStackTrace()
            }
        }
    }

    /**
     * 데이터 파싱
     */
    private fun ScanResult.toIBeaconData(): BeaconData? {
        return scanRecord?.bytes?.run {
            val address = device.address
            val rssi = rssi

            val structures = PayloadParser.getInstance().parse(this)
            for (structure in structures) {
                // IBeacon Parser
                if (structure is IBeacon) {
                    return@run BeaconData("$address(${structure.major},${structure.minor})", address.toLowerCase(), structure.major, structure.minor, rssi)
                }
            }
            return@run null
        }
    }

    private fun formatPayload(payload: ByteArray?): String? {
        if (payload == null || payload.isEmpty()) {
            return null
        }
        val payloadLength = getPayloadLength(payload)
        return toHexString(payload, payloadLength)
    }

    private fun getPayloadLength(payload: ByteArray): Int {
        var offset = 0
        while (offset < payload.size) {
            val length = payload[offset]
            if (length.toInt() == 0) {
                // the end of the content has been reached
                return offset
            } else if (length < 0) {
                // unexpected, take the full payload
                return payload.size
            }
            offset += 1 + length
        }
        return payload.size
    }

    private fun toHexString(bytes: ByteArray, length: Int): String {
        var lengthOfString = length
        val sb = StringBuffer()
        if (lengthOfString < 0 || lengthOfString > bytes.size) {
            lengthOfString = bytes.size
        }
        for (i in 0 until lengthOfString) {
            val b = bytes[i]
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private companion object {
        var TAG: String = BeaconScanner::class.java.simpleName

        val ANDROID_12_BLE_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            "android.permission.BLUETOOTH_SCAN"
        )
        val BLE_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH)

//        val permissions = arrayOf(
//                Manifest.permission.ACCESS_FINE_LOCATION,
//                Manifest.permission.ACCESS_COARSE_LOCATION,
//                Manifest.permission.BLUETOOTH_ADMIN,
//                Manifest.permission.BLUETOOTH)
    }
}