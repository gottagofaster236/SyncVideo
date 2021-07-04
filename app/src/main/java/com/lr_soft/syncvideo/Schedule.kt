package com.lr_soft.syncvideo

import java.time.LocalTime
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class Schedule(val scheduledVideos: List<ScheduledVideo>)

@Serializable
data class ScheduledVideo(val deviceId: String, val filename: String,
                          val startTime: LocalTime, val loop: Boolean)

private object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)
    // add a variable that'll parse/encode the thing in th right format

    override fun deserialize(decoder: Decoder): LocalTime {

    }

    override fun serialize(encoder: Encoder, value: LocalTime) {
        TODO("Not yet implemented")
    }

}