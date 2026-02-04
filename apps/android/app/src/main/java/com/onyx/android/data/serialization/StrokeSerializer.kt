package com.onyx.android.data.serialization

import com.onyx.android.ink.model.StrokeBounds
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.StrokeStyle
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SerializablePoint(
    val x: Float,
    val y: Float,
    val t: Long,
    val p: Float? = null,
    val tx: Float? = null,
    val ty: Float? = null,
    val r: Float? = null,
)

fun StrokePoint.toSerializable(): SerializablePoint =
    SerializablePoint(
        x = x,
        y = y,
        t = t,
        p = p,
        tx = tx,
        ty = ty,
        r = r,
    )

fun SerializablePoint.toStrokePoint(): StrokePoint =
    StrokePoint(
        x = x,
        y = y,
        t = t,
        p = p,
        tx = tx,
        ty = ty,
        r = r,
    )

@OptIn(ExperimentalSerializationApi::class)
object StrokeSerializer {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }

    fun serializePoints(points: List<StrokePoint>): ByteArray {
        val serializablePoints = points.map { it.toSerializable() }
        val jsonString = json.encodeToString(ListSerializer(SerializablePoint.serializer()), serializablePoints)
        return jsonString.toByteArray(Charsets.UTF_8)
    }

    fun deserializePoints(data: ByteArray): List<StrokePoint> {
        val jsonString = data.toString(Charsets.UTF_8)
        val serializablePoints =
            json.decodeFromString(ListSerializer(SerializablePoint.serializer()), jsonString)
        return serializablePoints.map { it.toStrokePoint() }
    }

    fun serializeStyle(style: StrokeStyle): String = json.encodeToString(style)

    fun deserializeStyle(data: String): StrokeStyle = json.decodeFromString(data)

    fun serializeBounds(bounds: StrokeBounds): String = json.encodeToString(bounds)

    fun deserializeBounds(data: String): StrokeBounds = json.decodeFromString(data)
}
