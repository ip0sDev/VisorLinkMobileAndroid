package org.visorlink.app.data.model

/**
 * Лайк поста канала — реакция 👍 на самом сообщении (functions/feed.js → `toggleLike`), её же
 * видно внутри канала. Своей копии у ленты нет: старая карточка `discover_feed` с `likeCount`
 * уходила в минус.
 */
const val FEED_LIKE_EMOJI = "👍"

/** Канал курируемого каталога «Рекомендуемые» (callable `getCuratedChannels`). */
data class CuratedChannel(
    val id: String,
    val name: String,
    val description: String = "",
    val avatarUrl: String? = null,
    /** Без «@». */
    val tag: String = "",
    val memberCount: Int = 0,
) {
    companion object {
        fun fromMap(m: Map<*, *>): CuratedChannel? {
            val id = (m["id"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            return CuratedChannel(
                id = id,
                name = m["name"] as? String ?: "",
                description = m["description"] as? String ?: "",
                avatarUrl = (m["avatarUrl"] as? String)?.takeIf { it.startsWith("http") },
                tag = (m["tag"] as? String ?: "").removePrefix("@"),
                memberCount = (m["memberCount"] as? Number)?.toInt() ?: 0,
            )
        }
    }
}

/**
 * Пост ленты «Каналы»: сообщение канала и сам канал (имя, аватар, настройки). Лайки и
 * просмотры читаются из сообщения.
 *
 * @property likeUids кто поставил 👍. Отдельным полем, чтобы [withLike] мог показать нажатие
 *   сразу, не дожидаясь сервера.
 */
data class FeedPost(
    val channel: Chat,
    val message: Message,
    val likeUids: Set<String> = likeUidsOf(message),
) {
    /** Ключ в списке и в отметках просмотров: id сообщений уникальны только внутри канала. */
    val key: String get() = keyOf(channel.id, message.id)

    val likeCount: Int get() = likeUids.size

    fun isLikedBy(uid: String): Boolean = uid in likeUids

    /** Канал разрешает реакции — иначе сервер лайк отклонит, кнопки нет. */
    val likesEnabled: Boolean get() = channel.settings.allowReactions

    /** Как `CommentsButton` в канале: разрешены и каналом, и самим постом. */
    val commentsEnabled: Boolean get() = channel.settings.allowComments && message.commentsEnabled != false

    /** «Запрет пересылки» канала закрывает и пересылку, и копирование, и сохранение. */
    val shareable: Boolean get() = !channel.settings.noForwards

    /** Текст поста или подпись к медиа. */
    val body: String? get() = (message.text?.takeIf { it.isNotBlank() } ?: message.caption)?.takeIf { it.isNotBlank() }

    /** Время поста; ещё не подтверждённый сервером — самый свежий. */
    val createdAtMillis: Long
        get() = message.createdAt?.let { it.seconds * 1000L + it.nanoseconds / 1_000_000L } ?: Long.MAX_VALUE

    /** Копия, где [uid] поставил ([liked]) или снял 👍. */
    fun withLike(uid: String, liked: Boolean): FeedPost =
        if (isLikedBy(uid) == liked) this else copy(likeUids = if (liked) likeUids + uid else likeUids - uid)

    val media: FeedMedia? get() = FeedMedia.of(message)

    companion object {
        fun keyOf(chatId: String, messageId: String) = "$chatId/$messageId"

        /** Реакции бывают и с повторами uid (старые клиенты) — считаем людей, а не `count`. */
        fun likeUidsOf(message: Message): Set<String> = message.reactions
            .filter { it["emoji"] == FEED_LIKE_EMOJI }
            .flatMap { (it["uids"] as? List<*>).orEmpty() }
            .filterIsInstance<String>()
            .toSet()
    }
}

/** Что показать в карточке поста помимо текста. */
sealed interface FeedMedia {
    /** Картинки (одна или альбом) — URL, которые можно открыть во вьювере. */
    data class Images(val urls: List<String>) : FeedMedia

    /** Вложение, которое лента не проигрывает сама (видео, голос, файл, подарок…): открыть в канале. */
    data class Attachment(val type: String) : FeedMedia

    /** Медиа со старого CDN — его больше нет, как «архивное вложение» в чате. */
    data object Archived : FeedMedia

    companion object {
        fun of(m: Message): FeedMedia? {
            if (m.type == MessageType.TEXT) return null
            if (isLegacyMediaMessage(m)) return Archived
            return when {
                m.type == MessageType.IMAGE ->
                    if (m.spoiler) Attachment(SPOILER) else m.url?.takeIf { it.isNotBlank() }?.let { Images(listOf(it)) } ?: Archived
                m.type.equals(MessageType.ALBUM, ignoreCase = true) -> {
                    if (m.spoiler || m.images.any { it.spoiler }) return Attachment(SPOILER)
                    val urls = m.images.mapNotNull { (it.url ?: it.previewUrl)?.takeIf { u -> u.isNotBlank() && !u.contains("api.visorlink.org") } }
                    if (urls.isEmpty()) Archived else Images(urls)
                }
                else -> Attachment(m.type)
            }
        }

        /** Тип [Attachment] для картинок под спойлером: в ленте их не раскрываем. */
        const val SPOILER = "spoiler"
    }
}

/** Чистые правила ленты «Каналы» (покрыты FeedModelsTest). */
object FeedPosts {
    /** Сколько последних сообщений каждого канала слушает лента — как веб. */
    const val PER_CHANNEL = 10

    /** Больше каналов не слушаем: берём самые свежие по последнему сообщению. */
    const val MAX_CHANNELS = 40

    /** Не больше стольких id за один вызов recordPostViews (MAX_VIEW_BATCH на сервере). */
    const val MAX_VIEW_BATCH = 20

    /**
     * Пост виден в ленте: не удалён, не скрыт по жалобам, опубликован. Свои неопубликованные
     * автор видит — так же фильтрует веб.
     */
    fun isVisible(m: Message, uid: String): Boolean =
        !m.deleted && m.isHidden != true && (m.isPublished != false || m.senderId == uid)

    fun channelsToFollow(chats: List<Chat>): List<Chat> = chats
        .filter { it.chatType() == ChatType.CHANNEL }
        .sortedByDescending { it.lastMessageAt?.seconds ?: 0L }
        .take(MAX_CHANNELS)

    /** Посты всех каналов одной лентой: новые сверху, при равном времени порядок стабилен. */
    fun assemble(channels: List<Chat>, messagesByChannel: Map<String, List<Message>>): List<FeedPost> =
        channels.flatMap { ch -> messagesByChannel[ch.id].orEmpty().map { FeedPost(ch, it) } }
            .distinctBy { it.key }
            .sortedWith(compareByDescending<FeedPost> { it.createdAtMillis }.thenBy { it.key })
}
