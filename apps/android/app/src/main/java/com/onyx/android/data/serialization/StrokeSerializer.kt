@file:OptIn(ExperimentalSerializationApi::class)
@file:Suppress("MagicNumber")

package com.onyx.android.data.serialization

import com.onyx.android.ink.model.StrokeBounds
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.StrokeStyle
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class SerializablePoint(
    @ProtoNumber(1)
    @SerialName("x")
    val x: Float,
    @ProtoNumber(2)
    @SerialName("y")
    val y: Float,
    @ProtoNumber(3)
    @SerialName("t")
    val t: Long,
    @ProtoNumber(4)
    @SerialName("p")
    val p: Float? = null,
    @ProtoNumber(5)
    @SerialName("tx")
    val tx: Float? = null,
    @ProtoNumber(6)
    @SerialName("ty")
    val ty: Float? = null,
    @ProtoNumber(7)
    @SerialName("r")
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

object StrokeSerializer {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }
    private val protoBuf = ProtoBuf { encodeDefaults = false }

    fun serializePoints(points: List<StrokePoint>): ByteArray {
        val serializablePoints = points.map { it.toSerializable() }
        return protoBuf.encodeToByteArray(ListSerializer(SerializablePoint.serializer()), serializablePoints)
    }

    fun deserializePoints(data: ByteArray): List<StrokePoint> {
        val serializablePoints =
            protoBuf.decodeFromByteArray(ListSerializer(SerializablePoint.serializer()), data)
        return serializablePoints.map { it.toStrokePoint() }
    }

    fun serializeStyle(style: StrokeStyle): String = json.encodeToString(style)

    fun deserializeStyle(data: String): StrokeStyle = json.decodeFromString(data)

    fun serializeBounds(bounds: StrokeBounds): String = json.encodeToString(bounds)

    fun deserializeBounds(data: String): StrokeBounds = json.decodeFromString(data)
}
