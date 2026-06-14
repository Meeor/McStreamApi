package kr.meeor.mcstreamapi.token

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

data class OAuthToken(
    val platform: String,
    val minecraftUuid: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val tokenType: String = "Bearer",
    val scope: String? = null,
    val channelId: String? = null,
    val channelName: String? = null,
    val minecraftPlayerName: String? = null,
) {
    fun toJson(): String {
        return Json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("platform", JsonPrimitive(platform))
                put("minecraftUuid", JsonPrimitive(minecraftUuid))
                put("minecraftPlayerName", JsonPrimitive(minecraftPlayerName))
                put("accessToken", JsonPrimitive(accessToken))
                put("refreshToken", JsonPrimitive(refreshToken))
                put("expiresAtEpochSeconds", JsonPrimitive(expiresAtEpochSeconds))
                put("tokenType", JsonPrimitive(tokenType))
                put("scope", JsonPrimitive(scope))
                put("channelId", JsonPrimitive(channelId))
                put("channelName", JsonPrimitive(channelName))
            },
        )
    }

    companion object {
        fun fromJson(value: String): Result<OAuthToken> {
            return runCatching {
                val root = Json.parseToJsonElement(value).jsonObject
                OAuthToken(
                    platform = root.requiredString("platform"),
                    minecraftUuid = root.requiredString("minecraftUuid"),
                    minecraftPlayerName = root.optionalString("minecraftPlayerName"),
                    accessToken = root.requiredString("accessToken"),
                    refreshToken = root.requiredString("refreshToken"),
                    expiresAtEpochSeconds = root.requiredLong("expiresAtEpochSeconds"),
                    tokenType = root.optionalString("tokenType") ?: "Bearer",
                    scope = root.optionalString("scope"),
                    channelId = root.optionalString("channelId"),
                    channelName = root.optionalString("channelName"),
                )
            }
        }

        private fun JsonObject.requiredString(key: String): String {
            return optionalString(key)?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Missing token field: $key")
        }

        private fun JsonObject.optionalString(key: String): String? {
            val value = this[key] ?: return null
            return value.jsonPrimitiveOrNull()?.contentOrNull
        }

        private fun JsonObject.requiredLong(key: String): Long {
            val value = this[key]?.jsonPrimitiveOrNull()?.longOrNull
            return value ?: throw IllegalArgumentException("Missing token field: $key")
        }

        private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveOrNull() =
            this as? kotlinx.serialization.json.JsonPrimitive
    }
}
