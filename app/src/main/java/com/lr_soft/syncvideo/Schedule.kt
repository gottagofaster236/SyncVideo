package com.lr_soft.syncvideo

import java.time.LocalTime
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.format.DateTimeFormatter

@Serializable
data class Schedule(val scheduledVideos: List<ScheduledVideo>)

@Serializable
data class ScheduledVideo(val deviceId: String,
                          val filename: String,
                          @Serializable(with = LocalTimeSerializer::class)
                          val startTime: LocalTime,
                          val loop: Boolean)

private object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalTime {
        return LocalTime.parse(decoder.decodeString())
    }

}