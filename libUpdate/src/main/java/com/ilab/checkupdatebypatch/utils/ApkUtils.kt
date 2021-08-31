package com.ilab.checkupdatebypatch.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File

object ApkUtils {
    /**
     * 删除patch
     * @param filePath 文件路径
     */
    fun delPatch(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists() && file.isFile) file.delete()
        } catch (ignore: Exception) {
        }
    }

    /**
     * 删除apk
     * @param filePath 文件路径
     */
    fun delApk(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists() && file.isFile) file.delete()
        } catch (ignore: Exception) {
        }
    }

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

    /**
     * 安装Apk
     *
     * @param context
     * @param apkPath
     */
    fun installApk(context: Context, apkPath: String, packageName: String) {
        try {
            val file = File(apkPath)
            val contentUri = FileProvider.getUriForFile(context, "$packageName.fileprovider", file)
            val intent = Intent()
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive")
            intent.setClassName(
                "com.android.packageinstaller",
                "com.android.packageinstaller.PackageInstallerActivity"
            )
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}