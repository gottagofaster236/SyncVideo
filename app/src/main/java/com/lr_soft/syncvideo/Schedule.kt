package com.lr_soft.syncvideo

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalTime

@Serializable
data class Schedule(val timezoneOffset: Int, val scheduledVideos: List<ScheduledVideo>) {
    fun filterByDeviceId(deviceId: String) =
        Schedule(timezoneOffset, scheduledVideos.filter { it.deviceId == deviceId })
}

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