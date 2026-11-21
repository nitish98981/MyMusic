package com.you.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import java.util.concurrent.ConcurrentHashMap

enum class Lang(val label: String, val code: String) {
    HINDI("Hindi", "hindi"),
    ENGLISH("English", "english")
}

data class Category(val title: String, val query: String)

data class ArtistEntry(val name: String, val query: String)

data class HomeData(
    val recommended: List<Song>,
    val fresh: List<Song>,
    val popular: List<Song>,
    val hits: List<Song>
) {
    val isEmpty: Boolean
        get() = recommended.isEmpty() && fresh.isEmpty() && popular.isEmpty() && hits.isEmpty()
}

data class HomeConfig(
    val recommendedDefault: String,
    val fresh: String,
    val popular: String,
    val hits: String,
    val rows: List<List<Category>>,
    val artists: List<ArtistEntry>
)

object HomeRepository {

    // ---- Yahan queries apni pasand se badal sakte ho ----
    private val hindi = HomeConfig(
        recommendedDefault = "romantic hindi songs",
        fresh = "new hindi songs 2026",
        popular = "trending hindi songs today",
        hits = "top hindi songs",
        rows = listOf(
            listOf(
                Category("Hindi Songs", "hindi songs"),
                Category("90s", "90s hindi songs"),
                Category("80s", "80s hindi songs"),
                Category("2000s", "2000s hindi songs"),
                Category("Retro", "old hindi classic songs")
            ),
            listOf(
                Category("Hindi Playlist", "hindi playlist best songs"),
                Category("Hindi Love Playlist", "hindi love songs"),
                Category("Party Playlist", "bollywood party songs"),
                Category("Sad Playlist", "hindi sad songs")
            ),
            listOf(
                Category("Punjabi Hits", "punjabi hit songs"),
                Category("Lo-fi Hindi", "hindi lofi songs"),
                Category("Devotional", "bhakti songs")
            )
        ),
        artists = listOf(
            ArtistEntry("KR\$NA", "KRSNA"),
            ArtistEntry("Yo Yo Honey Singh", "Yo Yo Honey Singh"),
            ArtistEntry("Arijit Singh", "Arijit Singh"),
            ArtistEntry("Badshah", "Badshah"),
            ArtistEntry("Diljit Dosanjh", "Diljit Dosanjh"),
            ArtistEntry("Atif Aslam", "Atif Aslam"),
            ArtistEntry("Shreya Ghoshal", "Shreya Ghoshal"),
            ArtistEntry("Neha Kakkar", "Neha Kakkar")
        )
    )

    private val english = HomeConfig(
        recommendedDefault = "english pop songs",
        fresh = "new english songs 2026",
        popular = "trending english songs today",
        hits = "top english songs",
        rows = listOf(
            listOf(
                Category("English Pop", "english pop songs"),
                Category("Hip Hop", "english hip hop hits"),
                Category("Rock", "rock hits"),
                Category("R&B", "rnb songs")
            ),
            listOf(
                Category("90s", "90s english hits"),
                Category("2000s", "2000s english hits"),
                Category("Retro", "classic english hits 80s")
            ),
            listOf(
                Category("Chill Playlist", "chill english songs"),
                Category("Workout Playlist", "english workout songs"),
                Category("Party Playlist", "english party songs"),
                Category("Lo-fi", "lofi songs")
            )
        ),
        artists = listOf(
            ArtistEntry("Taylor Swift", "Taylor Swift"),
            ArtistEntry("The Weeknd", "The Weeknd"),
            ArtistEntry("Ed Sheeran", "Ed Sheeran"),
            ArtistEntry("Billie Eilish", "Billie Eilish"),
            ArtistEntry("Dua Lipa", "Dua Lipa"),
            ArtistEntry("Eminem", "Eminem"),
            ArtistEntry("Justin Bieber", "Justin Bieber"),
            ArtistEntry("Imagine Dragons", "Imagine Dragons")
        )
    )

    fun config(lang: Lang): HomeConfig = if (lang == Lang.ENGLISH) english else hindi

    private val cache = ConcurrentHashMap<Lang, HomeData>()
    private val cachedAt = ConcurrentHashMap<Lang, Long>()

    private val collections = ConcurrentHashMap<String, List<Song>>()
    private val images = ConcurrentHashMap<String, String>()

    // Ek saath 3 se zyada search nahi, taaki YouTube block na kare
    private val gate = Semaphore(3)

    fun cachedData(lang: Lang): HomeData? = cache[lang]

    fun peekCollection(query: String): List<Song>? = collections[query]

    fun peekArtistImage(name: String): String? = images[name]

    suspend fun load(lang: Lang, force: Boolean = false): HomeData = coroutineScope {
        val old = cache[lang]
        val at = cachedAt[lang] ?: 0L
        if (!force && old != null && System.currentTimeMillis() - at < 30 * 60 * 1000L) {
            return@coroutineScope old
        }

        val cfg = config(lang)
                val a = async {
            val rec = try {
                Recommender.forYou(null, music = true, count = 10)
            } catch (e: Throwable) {
                emptyList()
            }
            if (rec.size >= 5) rec else safeSearch(recommendedQuery(cfg))
                }
        val b = async { safeSearch(cfg.fresh) }
        val c = async { safeSearch(cfg.popular) }
        val d = async { safeSearch(cfg.hits) }

        // Ek gaana kisi do rows me repeat na ho
        val recommended = a.await().take(10)
        val seen = recommended.map { it.url }.toMutableSet()
        val fresh = b.await().filter { seen.add(it.url) }.take(10)
        val popular = c.await().filter { seen.add(it.url) }.take(10)
        val hits = d.await().filter { seen.add(it.url) }.take(10)

        val data = HomeData(recommended, fresh, popular, hits)
        if (!data.isEmpty) {
            cache[lang] = data
            cachedAt[lang] = System.currentTimeMillis()
        }
        data
    }

    /** Ek category ke gaane (card dikhte hi ya tap karte hi load hote hain) */
    suspend fun collection(query: String): List<Song> {
        collections[query]?.let { return it }
        return gate.withPermit {
            collections[query]
                ?: safeSearch(query).take(30).also {
                    if (it.isNotEmpty()) collections[query] = it
                }
        }
    }

    /** Artist ki photo. Na mile to uske pehle gaane ka cover */
    suspend fun artistImage(entry: ArtistEntry): String? {
        images[entry.name]?.let { return it }

        val fromArtist = gate.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    val service = ServiceList.YouTube
                    val handler = service.searchQHFactory.fromQuery(
                        entry.query,
                        listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS),
                        ""
                    )
                    SearchInfo.getInfo(service, handler)
                        .relatedItems
                        .filterIsInstance<ChannelInfoItem>()
                        .firstOrNull()
                        ?.thumbnails
                        ?.maxByOrNull { it.height }
                        ?.url
                } catch (e: Throwable) {
                    null
                }
            }
        }

        val url = fromArtist ?: collection("${entry.query} songs").firstOrNull()?.thumb
        if (url != null) images[entry.name] = url
        return url
    }

    private suspend fun safeSearch(query: String): List<Song> =
        try {
            MusicRepository.search(query)
        } catch (e: Throwable) {
            emptyList()
        }

    /** Sabse zyada liked artist ke gaane, warna us bhasha ka default */
    private fun recommendedQuery(cfg: HomeConfig): String {
        val artist = Likes.liked.value
            .map { it.artist.split(",", "&").first().trim() }
            .filter { it.isNotEmpty() && it != "Unknown" }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        return if (artist != null) "$artist songs" else cfg.recommendedDefault
    }
}
