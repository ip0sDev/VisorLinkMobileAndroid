package org.visorlink.app.data.model

import com.google.firebase.firestore.PropertyName

data class LegalContainer(
    val version: String = "1.0.0",
    val versionCode: Int = 1,
    val lastUpdated: String? = null,
    val updatedAt: String? = null,
    val changelog: List<ChangelogEntry> = emptyList(),
    val meta: LegalMeta? = null,
    val tos: LegalDocument? = null,
    @get:PropertyName("privacy_policy")
    @set:PropertyName("privacy_policy")
    var privacyPolicy: LegalDocument? = null
)

data class ChangelogEntry(
    val version: String = "",
    val versionCode: Int = 0,
    val date: String = "",
    val changes: List<String> = emptyList()
)

data class LegalMeta(
    val version: String = "",
    val versionCode: Int = 0,
    val effectiveDate: String? = null,
    val updatedAt: String? = null,
    val serviceName: String = "VisorLink",
    val serviceUrl: String = "visorlink.org",
    val operator: String? = null,
    val contacts: LegalContacts? = null
)

data class LegalContacts(
    val supportEmail: String = "support@visorlink.org",
    val privacyEmail: String? = null,
    val legalEmail: String = "legal@visorlink.org",
    val abuseEmail: String? = null
)

data class LegalDocument(
    val id: String = "",
    val title: String = "",
    val subtitle: String? = null,
    val version: String? = null,
    val versionCode: Int? = null,
    val lastUpdated: String? = null,
    val introHtml: String? = null,
    val sections: List<LegalSection> = emptyList()
)

data class LegalSection(
    val id: String = "",
    val number: String = "",
    val title: String = "",
    val badge: String? = null,
    val keyPoints: List<String> = emptyList(),
    val content: String = ""
)
