package com.ilab.checkupdatebypatch.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File

object ApkUtils {
    /**
     * 获取已安装Apk文件的源Apk文件
     * 如：/data/app/com.sina.weibo-1.apk
     *
     * @param context
     * @param packageName
     * @return
     */
    fun getSourceApkPath(context: Context, packageName: String?): String? {
        packageName?.let {
            try {
                val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                return appInfo.sourceDir
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
        }
        return null
    }
}