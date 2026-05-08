package io.lpin.android.sdk.plac.scanner

import java.util.concurrent.ExecutionException

enum class ScannerType {
    CELL, BEACON, WIFI, LOCATION
}

class ScannerException : ExecutionException {
    enum class Type {
        NOT_SUPPORTED, PERMISSION_DENIED, DISABLED, SCAN_ALREADY_IN_PROGRESS, UNKNOWN_ERROR, TIMEOUT
    }

    var scannerType: ScannerType
    var scannerExceptionType: Type

    constructor(scannerType: ScannerType, scannerExceptionType: Type) : super("ScannerType: ${scannerType.name} Type: ${scannerExceptionType.name}") {
        this.scannerType = scannerType
        this.scannerExceptionType = scannerExceptionType
    }

    constructor(scannerType: ScannerType, scannerExceptionType: Type, message: String?) : super(message) {
        this.scannerType = scannerType
        this.scannerExceptionType = scannerExceptionType
    }

    constructor(scannerType: ScannerType, scannerExceptionType: Type, ex: Exception?) : super("ScannerType: ${scannerType.name} Type: ${scannerExceptionType.name}", ex) {
        this.scannerType = scannerType
        this.scannerExceptionType = scannerExceptionType
    }
}
