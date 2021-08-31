package com.ilab.checkupdatebypatch.utils

import com.arialyy.aria.core.Aria
import com.arialyy.aria.core.download.DownloadTaskListener
import com.arialyy.aria.core.task.DownloadTask

object MyAria : DownloadTaskListener {
    private var mTaskId: Long = -1
    private var mCallback: Callback? = null

    init {
        Aria.download(this).register()
    }

    fun download(url: String, dest: String, callback: Callback) {
        mCallback = callback
        mTaskId = Aria.download(this)
            .load(url)
            .setFilePath(dest)
            .ignoreFilePathOccupy()
            .create()
    }

    fun release() {
        Aria.download(this).load(mTaskId).cancel()
        mCallback = null
        Aria.download(this).unRegister()
    }

    override fun onWait(task: DownloadTask?) {}

    override fun onPre(task: DownloadTask?) {}

    override fun onTaskPre(task: DownloadTask?) {}

    override fun onTaskResume(task: DownloadTask?) {}

    override fun onTaskStart(task: DownloadTask?) {}

    override fun onTaskStop(task: DownloadTask?) {}

    override fun onTaskCancel(task: DownloadTask?) {}

    override fun onTaskFail(task: DownloadTask?, e: Exception?) {
        mCallback?.onFail(task)
    }

    override fun onTaskComplete(task: DownloadTask?) {
        mCallback?.onComplete(task)
    }

    override fun onTaskRunning(task: DownloadTask?) {
        mCallback?.onRunning(task)
    }

    override fun onNoSupportBreakPoint(task: DownloadTask?) {}

    interface Callback {
        fun onRunning(task: DownloadTask?) {}

        fun onFail(task: DownloadTask?) {}

        fun onComplete(task: DownloadTask?) {}
    }
}