package io.lpin.android.sdk.plac.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager

@SuppressLint("MissingPermission")
class CellScannerImpl(
    val context: Context
) : CellScanner {

    override fun init() {
        if (!ScannerValidate.hasPermission(context, permissions))
            throw ScannerException(ScannerType.CELL, ScannerException.Type.PERMISSION_DENIED)
        val tm = (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
        // 유심 상태 확인
        if (tm.simState == TelephonyManager.SIM_STATE_ABSENT || tm.simState == TelephonyManager.SIM_STATE_UNKNOWN) {
            throw ScannerException(ScannerType.CELL, ScannerException.Type.DISABLED)
        }
    }

    override fun getCellData(): LocationPackage? {
        val tm = (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
        // 유심 상태 확인
        if (tm.simState == TelephonyManager.SIM_STATE_ABSENT || tm.simState == TelephonyManager.SIM_STATE_UNKNOWN) {
            throw ScannerException(ScannerType.CELL, ScannerException.Type.DISABLED)
        }
        // 현재 통신사 확인
        val telecom = tm.networkOperatorName.run {
            if ("SKTelecom" == this) {
                "SKT"
            } else if ("KT" == this || "olleh" == this) {
                "KT"
            } else if (this.matches(Regex(".*LG.*"))) {
                "LGT"
            } else {
                this
            }
        }
        val mcc = try {
            tm.networkOperator.substring(0, 3)
        } catch (ignore: Exception) {
            throw ScannerException(ScannerType.CELL, ScannerException.Type.UNKNOWN_ERROR)
        }
        val mnc = try {
            tm.networkOperator.substring(3)
        } catch (ignore: Exception) {
            throw ScannerException(ScannerType.CELL, ScannerException.Type.UNKNOWN_ERROR)
        }

        return tm.allCellInfo
            ?.filter { it.isRegistered }
            ?.map {
                when (it) {
                    is CellInfoLte -> {
                        CellData(
                            mcc = mcc,
                            mnc = mnc,
                            cid = it.cellIdentity.ci.toLong(),
                            lac = it.cellIdentity.tac.toLong(),
                            telecom = telecom
                        )
                    }
                    is CellInfoGsm -> {
                        CellData(
                            mcc = mcc,
                            mnc = mnc,
                            cid = it.cellIdentity.cid.toLong(),
                            lac = it.cellIdentity.lac.toLong(),
                            telecom = telecom
                        )
                    }
                    is CellInfoWcdma -> {
                        CellData(
                            mcc = mcc,
                            mnc = mnc,
                            cid = it.cellIdentity.cid.toLong(),
                            lac = it.cellIdentity.lac.toLong(),
                            telecom = telecom
                        )
                    }
                    else -> throw ScannerException(
                        ScannerType.CELL,
                        ScannerException.Type.UNKNOWN_ERROR
                    )
                }
            }
            ?.map {
                LocationPackage().apply {
                    this.cell = it
                }
            }
            ?.firstOrNull()
    }

    companion object {
        var permissions = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}

