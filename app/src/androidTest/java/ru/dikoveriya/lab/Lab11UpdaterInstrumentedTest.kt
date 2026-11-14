package ru.dikoveriya.lab

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No HTTP or device modifications: updater's release trust decisions are deterministic. */
@RunWith(AndroidJUnit4::class)
class Lab11UpdaterInstrumentedTest {
    private fun release(url:String="https://github.com/artbahtiarov-web/dikoveriya-lab-/releases/download/v1.1.0-build.18/dikoveriya-lab-1.1.0-vc110018.apk",
        hash:String="sha256:"+"a".repeat(64)
    ):JSONObject=JSONObject().apply{
        put("tag_name","v1.1.0-build.18")
        put("draft",false);put("prerelease",false)
        put("html_url","https://github.com/artbahtiarov-web/dikoveriya-lab-/releases/tag/v1.1.0-build.18")
        put("body","Signed release")
        put("assets",JSONArray().put(JSONObject().apply{
            put("name","dikoveriya-lab-1.1.0-vc110018.apk")
            put("size",20_000_000L);put("digest",hash)
            put("state","uploaded");put("browser_download_url",url)
        }))
    }

    @Test fun verifiedReleaseIdentifiesDurableApkAndVersionCode(){
        val item=Lab11Updater.parseRelease(release())
        assertEquals("v1.1.0-build.18",item.tag)
        assertEquals(110018L,item.code)
        assertEquals(20_000_000L,item.size)
        assertEquals("a".repeat(64),item.sha256)
    }

    @Test fun wrongHostAndUnauthorizedPathsAreRejected(){
        val malicious=listOf(
            "https://example.com/evil.apk",
            "http://github.com/artbahtiarov-web/dikoveriya-lab-/releases/download/x/y.apk",
            "https://github.com/other/repo/releases/download/v1.1.0/x.apk",
            "https://github.com@evil.test/artbahtiarov-web/dikoveriya-lab-/releases/download/x/y.apk"
        )
        malicious.forEach{url->
            var rejected=false
            try{Lab11Updater.parseRelease(release(url))}
            catch(_:Exception){rejected=true}
            assertTrue("URL must be rejected: "+url,rejected)
        }
    }

    @Test fun missingChecksumAndPreReleaseAreRejected(){
        var rejected=false
        try{Lab11Updater.parseRelease(release(hash=""))}
        catch(_:Exception){rejected=true}
        assertTrue(rejected)
        rejected=false
        try{Lab11Updater.parseRelease(release().put("prerelease",true))}
        catch(_:Exception){rejected=true}
        assertTrue(rejected)
        rejected=false
        try{Lab11Updater.parseRelease(release().put("draft",true))}
        catch(_:Exception){rejected=true}
        assertTrue(rejected)
    }

    @Test fun releasesWithoutRealApkDoNotFallBackToTemporaryActionsArtifacts(){
        val data=release()
        data.put("assets",JSONArray().put(JSONObject().apply{
            put("name","app-debug.zip")
            put("size",1000)
            put("digest","sha256:"+"a".repeat(64))
            put("state","uploaded")
            put("browser_download_url","https://github.com/artbahtiarov-web/dikoveriya-lab-/releases/download/v1.1.0-build.18/app-debug.zip")
        }))
        var rejected=false
        try{Lab11Updater.parseRelease(data)}
        catch(_:Exception){rejected=true}
        assertTrue(rejected)
    }
}
