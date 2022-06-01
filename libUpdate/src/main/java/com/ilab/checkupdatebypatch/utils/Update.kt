package com.ilab.checkupdatebypatch.utils

import android.app.Application
import android.content.Context
import com.arialyy.aria.core.task.DownloadTask
import com.kunminx.architecture.ui.callback.UnPeekLiveData
import dev.utils.LogPrintUtils
import dev.utils.app.AppUtils
import dev.utils.app.ShellUtils
import dev.utils.common.FileUtils
import kotlinx.coroutines.*
import java.io.File

class Update {
    private var updateStatus = UnPeekLiveData<Pair<Status, Any?>>()
    private lateinit var versionName: String
    private lateinit var versionCode: String
    private lateinit var appContext: Application
    private lateinit var packageName: String
    private var viewModelScope: CoroutineScope? = null
    private var latestApkMd5: String? = null
    private var fileUrl: String? = null
    private var path: String? = null

    enum class Status {
        PATCH,          // 准备差量更新
        FULL,           // 准备全量更新
        LATEST,         // 当前为最新版本
        DOWNLOADING,    // 下载文件中
        FINISH,         // 下载完成
        MERGE,         // 下载完成
        ERROR           // 错误
    }

    init {
        try {
            System.loadLibrary("ApkPatchLibrary")
        } catch (ignore: Exception) {
        }
    }

    /**
     * 绑定更新实体
     *
     * @param scope 上下文作用域
     * @param triple first: app上下文; second: 包名; third: Pair(版本名, 版本号)
     * @param mutableData 用于接收回调
     */
    fun bind(
        scope: CoroutineScope,
        triple: Triple<Application, String, Pair<String, String>>,
        mutableData: UnPeekLiveData<Pair<Status, Any?>>
    ): Update {
        viewModelScope = scope
        appContext = triple.first
        packageName = triple.second
        versionName = triple.third.first
        versionCode = triple.third.second
        updateStatus = mutableData
        path = appContext.getDir("update", Context.MODE_PRIVATE).absolutePath + File.separator
        return this
    }

    /**
     * 获取版本信息
     *
     * @param data 接口返回的实体类
     * @param isManual 是否是手动触发
     * @param autoInstall 是否自动安装
     * @param isForce 是否强制安装
     */
    fun getVersionInfo(data: UpdateBean, isManual: Boolean, isForce: Boolean = true, autoInstall: Boolean = true) {
        latestApkMd5 = data.md5
        // 最新版本: 两个返回都是 null
        if (data.apkPath == null && data.patchPath == null) {
            updateStatus.postValue(Pair(Status.LATEST, isManual))
            return
        }
        // 全量更新
        if (data.patchPath == null) {
            parseApkInfo(data, autoInstall, isForce)
            return
        }
        // 差量更新
        if (data.apkPath == null) {
            parsePatchInfo(data, autoInstall, isForce)
            return
        }
    }

    // 解析差量patch信息
    private fun parsePatchInfo(data: UpdateBean, autoInstall: Boolean, isForce: Boolean) {
        fileUrl = data.patchPath
        if (autoInstall) download(isPatch = true, autoInstall = autoInstall, isForce = isForce)
        else updateStatus.postValue(
            Pair(
                Status.PATCH,
                Pair(data.message, parseLatestVersion(Regex(PATCH_REGEX), fileUrl!!))
            )
        )
    }

    // 解析全量apk信息
    private fun parseApkInfo(data: UpdateBean, autoInstall: Boolean, isForce: Boolean) {
        fileUrl = data.apkPath
        if (autoInstall) download(isPatch = false, autoInstall = autoInstall, isForce = isForce)
        else updateStatus.postValue(
            Pair(
                Status.FULL,
                Pair(data.message, parseLatestVersion(Regex(APK_REGEX), fileUrl!!))
            )
        )
    }

    // 从下载链接获取最新的versionName
    private fun parseLatestVersion(regex: Regex, path: String): String {
        var result = versionName
        regex.findAll(path).forEach { res ->
            if (res.value.isNotEmpty()) {
                result = res.value
            }
        }
        return result.replace("-", ".")
    }

    /**
     * 下载更新文件
     *
     * @param isPatch 是否是差量更新方式
     * @param autoInstall 是否全自动安装
     * @param isForce 是否强制安装
     */
    fun download(isPatch: Boolean, isForce: Boolean = true, autoInstall: Boolean = true) {
        viewModelScope?.launch(Dispatchers.IO) {
            FileUtils.deleteFile(path + NEW_APK_NAME)
            FileUtils.deleteFile(path + PATCH_FILE_NAME)
            // 保存文件的路径
            val loadedFilePath = if (isPatch) path + PATCH_FILE_NAME else path + NEW_APK_NAME
            fileUrl?.let {
                MyAria.download(it, loadedFilePath, object : MyAria.Callback {
                    override fun onRunning(task: DownloadTask?) {
                        updateStatus.postValue(Pair(Status.DOWNLOADING, task?.percent))
                    }

                    override fun onComplete(task: DownloadTask?) {
                        if (isPatch) mergeApk(autoInstall, isForce)
                        else {
                            if (!MD5Utils.checkMd5(path + NEW_APK_NAME, latestApkMd5)) {
                                updateStatus.postValue(Pair(Status.ERROR, MD5_CHECKED_ERROR))
                                LogPrintUtils.e("apk MD5校验失败")
                                return
                            }
                            updateStatus.postValue(Pair(Status.FINISH, null))
                            if (isForce) {
                                path?.run {
                                    if (autoInstall) {
                                        autoInstallApk(this + NEW_APK_NAME)
                                    } else {
                                        installApk(this + NEW_APK_NAME)
                                    }
                                }
                            }
                        }
                    }

                    override fun onFail(task: DownloadTask?) {
                        updateStatus.postValue(Pair(Status.ERROR, DOWNLOAD_ERROR))
                        LogPrintUtils.e("下载失败")
                    }
                })
            }
        }
    }

    // 合并APK
    private fun mergeApk(autoInstall: Boolean, isForce: Boolean) {
        viewModelScope?.launch(Dispatchers.IO) {
            updateStatus.postValue(Pair(Status.MERGE, null))
            val oldApkPath = ApkUtils.getSourceApkPath(appContext, packageName)
            // old apk不存在
            if (oldApkPath.isNullOrEmpty()) {
                updateStatus.postValue(Pair(Status.ERROR, MERGE_FILE_ERROR))
                LogPrintUtils.e("old apk不存在")
                return@launch
            }
            val mergeResult = PatchUtils.patch(
                oldApkPath,
                path + NEW_APK_NAME,
                path + PATCH_FILE_NAME
            )
            // 合并不成功
            if (mergeResult != 0) {
                updateStatus.postValue(Pair(Status.ERROR, MERGE_FILE_ERROR))
                LogPrintUtils.e("合并不成功")
                return@launch
            }
            // 检查合并后的apk与真实md5不相同
            if (!MD5Utils.checkMd5(path + NEW_APK_NAME, latestApkMd5)) {
                updateStatus.postValue(Pair(Status.ERROR, MD5_CHECKED_ERROR))
                LogPrintUtils.e("merge apk MD5校验失败")
                return@launch
            }
            withContext(Dispatchers.Main) {
                updateStatus.postValue(Pair(Status.FINISH, null))
                if (isForce) {
                    path?.run {
                        if (autoInstall) {
                            autoInstallApk(this + NEW_APK_NAME)
                        } else {
                            installApk(this + NEW_APK_NAME)
                        }
                    }
                }
            }
        }
    }

    /**
     * 显示界面安装apk
     */
    fun installApk(path: String?) {
        (path ?: this.path)?.let {
            val file = File(it)
            if (!file.exists() || !file.isFile) {
                updateStatus.postValue(Pair(Status.ERROR, FILE_MISS))
                LogPrintUtils.e(FILE_MISS)
                return
            }
            AppUtils.installApp(file)
        } ?: kotlin.run {
            updateStatus.postValue(Pair(Status.ERROR, NOT_FOUND_APK))
        }
    }

    /**
     * 静默安装apk
     */
    fun autoInstallApk(path: String?) {
        (path ?: this.path)?.let {
            val file = File(it)
            if (!file.exists() || !file.isFile) {
                updateStatus.postValue(Pair(Status.ERROR, FILE_MISS))
                LogPrintUtils.e(FILE_MISS)
                return
            }
            ShellUtils.execCmd("chmod 777 $it", true)
            AppUtils.installAppSilent(file, "-r", true)
        } ?: kotlin.run {
            updateStatus.postValue(Pair(Status.ERROR, NOT_FOUND_APK))
        }
    }

    /**
     * 释放下载器资源
     */
    fun release() {
        MyAria.release()
    }

    companion object {
        const val PATCH_REGEX = "(?<=--).*(?=.patch)"
        const val APK_REGEX = "(?=((?!/).)*\$).*(?=.apk)"
        const val FILE_MISS = "下载数据丢失"
        const val MD5_CHECKED_ERROR = "版本异常，无法升级"
        const val DOWNLOAD_ERROR = "下载文件异常"
        const val MERGE_FILE_ERROR = "文件校验失败"
        const val PATCH_FILE_NAME = "patchfile.patch"
        const val NEW_APK_NAME = "new.apk"
        const val NOT_FOUND_APK = "未找到安装包"
        const val GET_UPDATE_INFO_ERROR = "获取版本信息异常"
        const val MD5_CHECKED_ALWAYS_ERROR = "校验文件持续异常，请稍后再试"
    }
}