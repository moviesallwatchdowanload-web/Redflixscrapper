// language: Kotlin, file: RedflixProvider.kt
// Redflix — TMDB metadata + videasy/vidlink stream source
// CloudStream/XeonMovies compatible plugin
package com.redflix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

class RedflixProvider : MainAPI() {
    override var mainUrl = "https://redflix.club"
    override var name = "Redflix"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val lang = "en"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val tmdbBase = "$mainUrl/api/tmdb"
    private val imgBase = "https://image.tmdb.org/t/p/w500"
    private val imgBaseBig = "https://image.tmdb.org/t/p/w1280"

    private fun poster(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$imgBase$it" }

    private fun backdrop(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$imgBaseBig$it" }

    private fun fixMediaType(mt: String?) = when (mt) {
        "movie" -> TvType.Movie
        "tv" -> TvType.TvSeries
        else -> TvType.Movie
    }

    private fun parseJson(txt: String) = JSONObject(txt)

    private fun yearOf(json: JSONObject): Int? =
        (json.optString("release_date").ifBlank { json.optString("first_air_date") })
            .take(4).toIntOrNull()

    private fun titleOf(json: JSONObject): String =
        json.optString("title").ifBlank { json.optString("name") }

    override val mainPage = mainPageOf(
        "trending/all/day" to "Trending Today",
        "trending/all/week" to "Trending This Week",
        "movie/popular" to "Popular Movies",
        "movie/now_playing" to "Now Playing",
        "movie/top_rated" to "Top Rated Movies",
        "tv/popular" to "Popular TV Shows",
        "tv/on_the_air" to "Currently Airing",
        "tv/top_rated" to "Top Rated TV",
        "trending/movie/day" to "Trending Movies",
        "trending/tv/day" to "Trending TV"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$tmdbBase/${request.data}?page=$page"
        val json = parseJson(app.get(url).text)
        val results = json.optJSONArray("results")
            ?: return newHomePageResponse(request.name, emptyList())

        val items = (0 until results.length()).mapNotNull { i ->
            val it = results.getJSONObject(i)
            if (it.optBoolean("adult", false)) return@mapNotNull null
            val mt = it.optString("media_type", request.data.substringBefore("/"))
            val type = fixMediaType(mt)
            val title = titleOf(it)
            val id = it.optInt("id")
            if (id == 0 || title.isBlank()) return@mapNotNull null

            newMovieSearchResponse(
                name = title,
                url = "$mainUrl/$mt/$id",
                type = type
            ) {
                this.posterUrl = poster(it.optString("poster_path"))
                this.year = yearOf(it)
                this.score = Score.from10(it.optDouble("vote_average").times(10).toInt())
            }
        }

        return newHomePageResponse(
            request.name,
            items,
            hasNext = page < (json.optInt("total_pages", 1))
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$tmdbBase/search/multi?query=${query.encodeUrl()}&page=1"
        val json = parseJson(app.get(url).text)
        val results = json.optJSONArray("results") ?: return emptyList()

        return (0 until results.length()).mapNotNull { i ->
            val it = results.getJSONObject(i)
            if (it.optBoolean("adult", false)) return@mapNotNull null
            val mt = it.optString("media_type")
            if (mt != "movie" && mt != "tv") return@mapNotNull null
            val type = fixMediaType(mt)
            val title = titleOf(it)
            val id = it.optInt("id")
            if (id == 0 || title.isBlank()) return@mapNotNull null

            newMovieSearchResponse(
                name = title,
                url = "$mainUrl/$mt/$id",
                type = type
            ) {
                this.posterUrl = poster(it.optString("poster_path"))
                this.year = yearOf(it)
                this.score = Score.from10(it.optDouble("vote_average").times(10).toInt())
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val parts = url.removePrefix("$mainUrl/").split("/")
        if (parts.size < 2) return null
        val mt = parts[0]
        val id = parts[1]
        val type = fixMediaType(mt)

        val json = parseJson(app.get("$tmdbBase/$mt/$id").text)
        val title = titleOf(json)
        if (title.isBlank()) return null

        val genres = json.optJSONArray("genres")?.let { g ->
            (0 until g.length()).map { g.getJSONObject(it).optString("name") }
        } ?: emptyList()

        return newMovieLoadResponse(title, url, type, url) {
            this.posterUrl = poster(json.optString("poster_path"))
            this.backgroundPosterUrl = backdrop(json.optString("backdrop_path"))
            this.plot = json.optString("overview").ifBlank { null }
            this.year = yearOf(json)
            this.tags = genres
            this.score = Score.from10(json.optDouble("vote_average").times(10).toInt())
            this.duration = json.optInt("runtime", 0).takeIf { it > 0 }

            if (type == TvType.TvSeries) {
                val seasons = json.optJSONArray("seasons")
                if (seasons != null) {
                    val eps = mutableListOf<Episode>()
                    for (s in 0 until seasons.length()) {
                        val season = seasons.getJSONObject(s)
                        val sn = season.optInt("season_number")
                        if (sn <= 0) continue
                        val seasonJson = try {
                            parseJson(app.get("$tmdbBase/tv/$id/season/$sn").text)
                        } catch (e: Exception) { null }
                        val episodeArray = seasonJson?.optJSONArray("episodes")
                        if (episodeArray != null) {
                            for (e in 0 until episodeArray.length()) {
                                val ep = episodeArray.getJSONObject(e)
                                val en = ep.optInt("episode_number")
                                eps.add(newEpisode("$url/$sn/$en") {
                                    this.name = ep.optString("name").ifBlank { "Episode $en" }
                                    this.season = sn
                                    this.episode = en
                                    this.posterUrl = poster(ep.optString("still_path"))
                                    this.description = ep.optString("overview").ifBlank { null }
                                })
                            }
                        } else {
                            val count = season.optInt("episode_count")
                            for (en in 1..count) {
                                eps.add(newEpisode("$url/$sn/$en") {
                                    this.season = sn
                                    this.episode = en
                                })
                            }
                        }
                    }
                    this.episodes = eps
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val path = data.removePrefix("$mainUrl/").split("/")
        if (path.size < 2) return false
        val mt = path[0]
        val id = path[1]

        val sources = mutableListOf<Pair<String, String>>()

        when {
            mt == "movie" -> {
                sources += "Videasy" to "https://player.videasy.net/movie/$id"
                sources += "Vidlink" to "https://vidlink.pro/movie/$id"
            }
            mt == "tv" && path.size >= 4 -> {
                val s = path[2]
                val e = path[3]
                sources += "Videasy" to "https://player.videasy.net/tv/$id/$s/$e"
                sources += "Vidlink" to "https://vidlink.pro/tv/$id/$s/$e"
            }
            else -> return false
        }

        sources.forEach { (name, url) ->
            try {
                loadExtractor(url, data, subtitleCallback) { link ->
                    callback(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = link.url,
                            type = link.type
                        ) {
                            this.referer = "$mainUrl/"
                            this.quality = link.quality
                            this.headers = link.headers + mapOf(
                                "Referer" to "$mainUrl/",
                                "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36"
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                // ek source fail ho toh doosra try karo
            }
        }

        return true
    }
}
