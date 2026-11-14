package ru.dikoveriya.lab

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/** Only durable public GitHub Releases; temporary Actions artifacts are not updates. */
internal data class Lab11PublishedUpdate(
    val tag:String, val code:Long, val apkUrl:String, val size:Long,
    val sha256:String, val pageUrl:String, val notes:String
)

internal object Lab11Updater {
    private const val OWNER="artbahtiarov-web"
    private const val REPO="dikoveriya-lab-"
    private const val API="https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val MIME="application/vnd.android.package-archive"
    private const val MAX_BYTES=150L*1024L*1024L
    private val allowedName=Regex("""^dikoveriya-lab-[0-9][a-zA-Z0-9.+_-]*-vc([0-9]+)\.apk$""")
    private val checksum=Regex("^[a-fA-F0-9]{64}$")

    fun installedCode(context:Context):Long{
        val pkg=context.packageManager.getPackageInfo(context.packageName,0)
        return if(Build.VERSION.SDK_INT>=28)pkg.longVersionCode else pkg.versionCode.toLong()
    }
    fun installedVersion(context:Context):String=
        context.packageManager.getPackageInfo(context.packageName,0).versionName?:"неизвестна"

    /** No GitHub credentials are embedded in the APK. */
    suspend fun latest():Lab11PublishedUpdate=withContext(Dispatchers.IO){
        val conn=(URL(API).openConnection() as HttpURLConnection).apply{
            connectTimeout=15000;readTimeout=20000
            setRequestProperty("Accept","application/vnd.github+json")
            setRequestProperty("User-Agent","Dikoveriya-LAB-Android-Updater")
        }
        try{
            if(conn.responseCode==404)
                error("В GitHub Releases пока нет опубликованных обновлений")
            require(conn.responseCode==200){"Ошибка GitHub: HTTP "+conn.responseCode}
            val root=JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).use{
                it.readText()
            })
            parseRelease(root)
        }finally{conn.disconnect()}
    }

    /** Parser is isolated from the network and exercised with malicious release fixtures. */
    internal fun parseRelease(root:JSONObject):Lab11PublishedUpdate{
            require(!root.optBoolean("draft") && !root.optBoolean("prerelease")){
                "Предварительная версия не предлагается для обновления"
            }
            val tag=root.getString("tag_name")
            require(tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+.][A-Za-z0-9.]+)?"))){
                "Неправильная версия релиза"
            }
            val assets=root.getJSONArray("assets")
            val candidates=buildList{
                for(i in 0 until assets.length()){
                    val asset=assets.getJSONObject(i)
                    val m=allowedName.matchEntire(asset.optString("name"))?:continue
                    if(asset.optString("state")!="uploaded")continue
                    val size=asset.optLong("size")
                    val sha=asset.optString("digest").removePrefix("sha256:")
                    val url=asset.optString("browser_download_url")
                    if(size !in 1..MAX_BYTES || !checksum.matches(sha) ||
                        !trustedUrl(url))continue
                    add(Lab11PublishedUpdate(tag,m.groupValues[1].toLong(),url,size,
                        sha.lowercase(Locale.ROOT),root.getString("html_url"),
                        root.optString("body").take(2500)))
                }
            }
            return candidates.maxByOrNull{it.code}
                ?:error("В опубликованной версии нет проверяемого APK")
    }

    private fun trustedUrl(value:String):Boolean{
        val uri=runCatching{Uri.parse(value)}.getOrNull()?:return false
        return uri.scheme=="https" && uri.host=="github.com" &&
            uri.path?.startsWith("/$OWNER/$REPO/releases/download/")==true &&
            uri.userInfo==null && uri.port==-1
    }

    /** Validates network checksum, package ID, build number and permanent signature. */
    suspend fun download(context:Context,update:Lab11PublishedUpdate,
        progress:suspend (Int)->Unit
    ):File=withContext(Dispatchers.IO){
        require(trustedUrl(update.apkUrl)){"Недостоверная ссылка"}
        require(update.code>installedCode(context)){"Эта версия уже установлена"}
        val folder=File(context.cacheDir,"updates")
        check(folder.isDirectory || folder.mkdirs()){"Не удалось создать папку загрузки"}
        val target=File(folder,"dikoveriya-update-"+update.code+".apk")
        val temp=File(folder,"downloading-"+update.code+".part")
        temp.delete()
        val conn=(URL(update.apkUrl).openConnection() as HttpURLConnection).apply{
            connectTimeout=20000;readTimeout=30000;instanceFollowRedirects=true
            setRequestProperty("User-Agent","Dikoveriya-LAB-Android-Updater")
            setRequestProperty("Accept","application/octet-stream")
        }
        try{
            require(conn.responseCode==200){"Ошибка скачивания: HTTP "+conn.responseCode}
            require(conn.url.protocol=="https"){"Небезопасная переадресация"}
            val declared=conn.contentLengthLong
            require(declared<0 || declared==update.size){"Размер файла отличается от релиза"}
            val hash=MessageDigest.getInstance("SHA-256")
            var total=0L
            var shown=-1
            conn.inputStream.use{input->
                temp.outputStream().use{output->
                    val buffer=ByteArray(32768)
                    while(true){
                        currentCoroutineContext().ensureActive()
                        val n=input.read(buffer)
                        if(n<0)break
                        total+=n
                        require(total<=MAX_BYTES && total<=update.size){
                            "Размер APK превышает ожидаемый"
                        }
                        hash.update(buffer,0,n)
                        output.write(buffer,0,n)
                        val percent=(total*100/update.size).toInt().coerceIn(0,100)
                        if(percent!=shown){
                            shown=percent
                            withContext(Dispatchers.Main){progress(percent)}
                        }
                    }
                }
            }
            require(total==update.size){"APK скачан не полностью"}
            val actual=hash.digest().joinToString(""){"%02x".format(it)}
            require(actual.equals(update.sha256,true)){
                "SHA-256 файла не совпадает с опубликованным значением"
            }
            verifyPackage(context,temp,update.code)
            target.delete()
            check(temp.renameTo(target)){"Не удалось подготовить скачанный APK"}
            target
        }finally{conn.disconnect();temp.delete()}
    }

    private fun signatureHashes(info:PackageInfo):List<ByteArray>{
        val signatures=if(Build.VERSION.SDK_INT>=28)
            info.signingInfo?.apkContentsSigners
        else info.signatures
        return (signatures?:emptyArray()).map{
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
        }
    }
    private fun verifyPackage(context:Context,file:File,expectedCode:Long){
        val pm=context.packageManager
        val flags=if(Build.VERSION.SDK_INT>=28)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val downloaded=pm.getPackageArchiveInfo(file.absolutePath,flags)
            ?:error("Скачан не Android APK")
        require(downloaded.packageName==context.packageName){
            "Файл не принадлежит приложению Диковерия LAB"
        }
        val actualCode=if(Build.VERSION.SDK_INT>=28)downloaded.longVersionCode
            else downloaded.versionCode.toLong()
        require(actualCode==expectedCode && actualCode>installedCode(context)){
            "Номер версии APK не совпадает с релизом"
        }
        val installed=pm.getPackageInfo(context.packageName,flags)
        val old=signatureHashes(installed)
        val fresh=signatureHashes(downloaded)
        require(old.isNotEmpty() && old.size==fresh.size &&
            old.all{x->fresh.any{y->MessageDigest.isEqual(x,y)}}){
            "Сертификат APK не совпадает с установленной Диковерия LAB"
        }
    }

    /** The Android package installer always asks the user for confirmation. */
    fun requestInstall(context:Context,apk:File):Boolean{
        require(apk.isFile && apk.length()>0){"APK не найден"}
        if(Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()){
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:"+context.packageName)).apply{
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return false
        }
        val uri=FileProvider.getUriForFile(context,context.packageName+".share",apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply{
            setDataAndType(uri,MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return true
    }
}
