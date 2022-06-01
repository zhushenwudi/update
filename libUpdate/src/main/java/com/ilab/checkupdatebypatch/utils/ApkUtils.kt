package com.ilab.checkupdatebypatch.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.utils.LogPrintUtils
import dev.utils.app.AppUtils
import dev.utils.app.PathUtils
import dev.utils.app.ResourceUtils
import dev.utils.app.ShellUtils
import dev.utils.common.FileUtils
import java.io.File

object ApkUtils {
    const val DAEMON_PACKAGE = "com.ilab.cabinetdaemon"
    const val DAEMON_CLASS = "com.ilab.cabinetdaemon.MainActivity"

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
     * 安装守护进程
     */
    fun installDaemon(): Boolean {
        val isInstalled = AppUtils.isInstalledApp(DAEMON_PACKAGE)
        if (isInstalled) {
            return false
        }
        var isSuccess = false
        val apkName = "daemon.apk"
        val dest = PathUtils.getSDCard().sdCardPath + File.separator + apkName
        try {
            FileUtils.copyFile(ResourceUtils.getAssets().open(apkName), dest, true)
            val res = ShellUtils.execCmd("chmod 777 $dest", true)
            if (res.result == 0) {
                isSuccess = AppUtils.installAppSilent(File(dest), "-r", true)
            } else {
                LogPrintUtils.e(res.errorMsg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            LogPrintUtils.e("APP 中未找到 APK 文件")
        }
        return isSuccess
    }

    /**
     * 开启守护进程
     */
    fun openDaemon(
        context: Context,
        appPkgName: String,
        appClzName: String
    ) {
        val isInstalled = AppUtils.isInstalledApp(DAEMON_PACKAGE)
        if (isInstalled) {
            val mIntent = Intent()
            val componentName = ComponentName(DAEMON_PACKAGE, DAEMON_CLASS)
            mIntent.component = componentName
            mIntent.putExtra("pkg", appPkgName)
            mIntent.putExtra("cls", appClzName)
            mIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(mIntent)
        }
    }

    /**
     * 删除APK
     */
    fun removeApk(
        context: Context,
        filePath: String = context.getDir(
            "update",
            Context.MODE_PRIVATE
        ).absolutePath + File.separator + Update.NEW_APK_NAME
    ): Boolean {
        var isSuccess = false
        val file = File(filePath)
        if (file.exists() && file.isFile) {
            isSuccess = FileUtils.deleteFile(file)
        }
        return isSuccess
    }
}