package com.ilab.checkupdatebypatch.utils

import androidx.annotation.Keep

@Keep
data class UpdateBean(
    var apkPath: String?,
    var patchPath: String?,
    var md5: String?,
    var message: String?
)