[![GitHub](https://img.shields.io/badge/GitHub-update-blue.svg)]()
[![GitHub license](https://img.shields.io/github/license/zhushenwudi/update.svg)](https://github.com/zhushenwudi/update/blob/master/LICENCE)
[![Jitpack](https://img.shields.io/badge/update-1.7-brightgreen)]()

## 引入方法


```groovy
allprojects {
	repositories {
		...
		maven { url 'https://jitpack.io' }
	}
}
```

```groovy
dependencies {
    implementation 'com.github.zhushenwudi:update:1.7'
}
```


## 使用案例

在application.kt中

初始化：

```kotlin
val appInfo by lazy {
	Triple(
		appContext,
		AppUtils.getApplicationInfo().packageName,
		Pair(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString())
	)
}
	
val updateStatus = EventLiveData<Pair<Update.Status, Any?>>()
val update by lazy { Update().bind(mCoroutineScope, appInfo, updateStatus) }
```
	
释放资源：

```kotlin
update.release()
```
	
回调：

```kotlin
App.instance.updateStatus.observe(viewLifecycleOwner) {
	when (it.first) {
		Update.Status.PATCH, Update.Status.FULL -> {
			/** 弹窗升级 **/
			App.instance.update.download(isPatch = it.first == Update.Status.PATCH, autoInstall = false)
		}
      Update.Status.LATEST -> {
         /** 最新版本 **/
      }
      Update.Status.DOWNLOADING -> {
         /** 下载中，更新进度 **/
         Log.d("aaa", "下载进度: ${it.second as Int}")
      }
      Update.Status.FINISH -> {
         /** 结束下载 **/
      }
      Update.Status.ERROR -> {
         when (it.second) {
         		Update.MD5_CHECKED_ERROR, Update.MERGE_FILE_ERROR -> {
         			/** 尝试重下 apk 更新 **/
         			App.instance.update.getVersionInfo(data = UpdateBean(), isManual = false, autoInstall = true)
             }
             Update.GET_UPDATE_INFO_ERROR -> {
                /** 获取版本信息失败 **/
             }
             else -> {
             		Log.d("aaa", it.second as String)
             }
        }
     }
     else -> {
     }
	}
}
```
