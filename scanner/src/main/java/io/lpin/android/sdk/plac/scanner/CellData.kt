package io.lpin.android.sdk.plac.scanner

data class CellData(
    val mcc: String,
    val mnc: String,
    val cid: Long,
    val lac: Long,
    val telecom: String
) {
    override fun equals(other: Any?): Boolean {
        if (other is CellData) {
            return other.cid == cid &&
                    other.lac == lac &&
                    other.telecom == telecom &&
                    other.mcc == mcc &&
                    other.mnc == mnc
        }
        return false
    }

    override fun hashCode(): Int {
        var result = mcc.hashCode()
        result = 31 * result + mnc.hashCode()
        result = 31 * result + cid.hashCode()
        result = 31 * result + lac.hashCode()
        result = 31 * result + telecom.hashCode()
        return result
    }
}