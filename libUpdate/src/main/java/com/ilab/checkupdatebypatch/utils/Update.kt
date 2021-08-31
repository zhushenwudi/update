package com.ilab.checkupdatebypatch.utils

import android.app.Application
import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.arialyy.aria.core.task.DownloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class Update {
    private var updateStatus = MutableLiveData<Pair<Status, Any?>>()
    private lateinit var versionName: String
    private lateinit var versionCode: String
    private lateinit var appContext: Application
    private lateinit var packageName: String
    private var viewModelScope: CoroutineScope? = null
    private var latestApkMd5: String? = null
    private var newMD5: String? = null
    private var fileUrl: String? = null
    private var path: String? = null

    enum class Status {
        PATCH,          // 准备差量更新
        FULL,           // 准备全量更新
        LATEST,         // 当前为最新版本
        READY,          // 准备下载文件
        DOWNLOADING,    // 下载文件中
        FINISH,         // 下载完成
        MERGE,         // 下载完成
        ERROR           // 错误
    }

    init {
        try {
            System.loadLibrary("ApkPatchLibrary")
        } catch (ignore: Exception) {}
    }

    fun bind(
        scope: CoroutineScope,
        triple: Triple<Application, String, Pair<String, String>>,
        mutableData: MutableLiveData<Pair<Status, Any?>>
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

    // 获取版本信息
    fun getVersionInfo(data: UpdateBean, needRetry: Boolean, isManual: Boolean) {
        if (needRetry) latestApkMd5 = data.md5
        // 最新版本两个返回都是 null
        if (data.apkPath == null && data.patchPath == null) {
            updateStatus.postValue(Pair(Status.LATEST, isManual))
            return
        }
        // 隔代更新
        if (data.patchPath == null) {
            parseApkInfo(data, needRetry)
            return
        }
        // 差量更新
        if (data.apkPath == null) {
            parsePatchInfo(data)
            return
        }
    }

    // 解析差量patch信息
    private fun parsePatchInfo(data: UpdateBean) {
        newMD5 = data.md5
        fileUrl = data.patchPath
        updateStatus.postValue(
            Pair(
                Status.PATCH,
                Pair(data.message, parseLatestVersion(Regex(PATCH_REGEX), fileUrl!!))
            )
        )
    }

    // 解析全量apk信息
    private fun parseApkInfo(data: UpdateBean, needRetry: Boolean) {
        fileUrl = data.apkPath
        if (needRetry) download(false, needRetry)
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

    // 下载更新文件
    fun download(isPatch: Boolean, needRetry: Boolean = false) {
        viewModelScope?.launch(Dispatchers.IO) {
            updateStatus.postValue(Pair(Status.READY, null))
            ApkUtils.delApk(path + NEW_APK_NAME)
            ApkUtils.delPatch(path + PATCH_FILE_NAME)
            // 保存文件的路径
            val loadedFilePath = if (isPatch) path + PATCH_FILE_NAME else path + NEW_APK_NAME
            fileUrl?.let {
                MyAria.download(it, loadedFilePath, object : MyAria.Callback {
                    override fun onRunning(task: DownloadTask?) {
                        updateStatus.postValue(Pair(Status.DOWNLOADING, task?.percent))
                    }

                    override fun onComplete(task: DownloadTask?) {
                        if (isPatch) mergeApk()
                        else {
                            if (needRetry && !SignUtils.checkMd5(path + NEW_APK_NAME, latestApkMd5)) {
                                updateStatus.postValue(Pair(Status.ERROR, MD5_CHECKED_ERROR))
                                return
                            }
                            installApk()
                        }
                    }

                    override fun onFail(task: DownloadTask?) {
                        updateStatus.postValue(Pair(Status.ERROR, DOWNLOAD_ERROR))
                    }
                })
            }
        }
    }

    // 合并APK
    private fun mergeApk() {
        viewModelScope?.launch(Dispatchers.IO) {
            updateStatus.postValue(Pair(Status.MERGE, null))
            val oldApkPath = ApkUtils.getSourceApkPath(appContext, packageName)
            // old apk不存在
            if (oldApkPath.isNullOrEmpty()) {
                updateStatus.postValue(Pair(Status.ERROR, MERGE_FILE_ERROR))
                return@launch
            }
            val mergeResult = PatchUtils.patch(oldApkPath, path + NEW_APK_NAME, path + PATCH_FILE_NAME)
            // 合并不成功 || 检查合并后的apk与真实md5不相同
            if (mergeResult != 0 || !SignUtils.checkMd5(path + NEW_APK_NAME, newMD5)) {
                updateStatus.postValue(Pair(Status.ERROR, MERGE_FILE_ERROR))
                return@launch
            }
            withContext(Dispatchers.Main) {
                installApk()
            }
        }
    }

    // 安装apk
    private fun installApk() {
        updateStatus.postValue(Pair(Status.FINISH, null))
        path?.let {
            if (it.isNotEmpty()) {
                val fileName = it + NEW_APK_NAME
                val file = File(fileName)
                if (!file.exists() || !file.isFile) {
                    updateStatus.postValue(Pair(Status.ERROR, FILE_MISS))
                    return
                }
                ApkUtils.installApk(appContext, fileName, packageName)
            }
        }
    }

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
        const val GET_UPDATE_INFO_ERROR = "获取版本信息异常"
        const val PATCH_FILE_NAME = "patchfile.patch"
        const val NEW_APK_NAME = "new.apk"
    }
}