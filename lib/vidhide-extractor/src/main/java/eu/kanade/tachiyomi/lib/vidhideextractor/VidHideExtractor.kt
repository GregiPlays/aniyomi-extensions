package eu.kanade.tachiyomi.lib.vidhideextractor

import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient

class VidHideExtractor(private val client: OkHttpClient, private val headers: Headers) {

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    fun videosFromUrl(url: String, videoNameGen: (String) -> String = { quality -> "VidHide - $quality" }): List<Video> {
        try {
            val doc = client.newCall(GET(url, headers)).execute()
                .asJsoup()

            val scriptBody = doc.selectFirst("script:containsData(m3u8)")
                ?.data()
                ?: return emptyList()

            val masterUrl = extractMasterUrl(scriptBody)
            val subtitleList = extractSubtitleList(scriptBody)

            return playlistUtils.extractFromHls(
                masterUrl,
                url,
                videoNameGen = videoNameGen,
                subtitleList = subtitleList,
            )
        } catch (e: Exception) {
            // Handle exception
            return emptyList()
        }
    }

    private fun extractMasterUrl(scriptBody: String): String? {
        val pattern = Regex("source\\s*=\\s*\"(.*?)\"")
        return pattern.find(scriptBody)?.groupValues?.get(1)
    }

    private fun extractSubtitleList(scriptBody: String): List<Track> {
        try {
            val subtitleStr = scriptBody
                .substringAfter("tracks")
                .substringAfter("[")
                .substringBefore("]")
            val parsed = json.decodeFromString<List<TrackDto>>("[$subtitleStr]")
            return parsed.filter { it.kind.equals("captions", true) }
                .map { Track(it.file, it.label ?: "") }
        } catch (e: SerializationException) {
            // Handle exception
            return emptyList()
        }
    }

    @Serializable
    class TrackDto(
        val file: String,
        val kind: String,
        val label: String? = null,
    )
}
