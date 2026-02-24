package com.onyx.android.objects.model

import kotlinx.serialization.Serializable

@Serializable
data class ImagePayload(
    val assetId: String? = null,
    val mimeType: String? = null,
    val sourceUri: String? = null,
    val displayName: String? = null,
)
