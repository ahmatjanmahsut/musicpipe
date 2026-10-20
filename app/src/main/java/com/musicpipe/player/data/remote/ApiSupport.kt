package com.musicpipe.player.data.remote

import com.google.gson.Gson
import com.musicpipe.player.data.remote.dto.ErrorDto
import retrofit2.Response

/** Error shape from docs/01 §3.0: { error: machine_code, message }. */
class ApiException(
    val code: String,
    override val message: String,
    val httpStatus: Int
) : Exception("[$httpStatus $code] $message") {
    companion object {
        private val gson = Gson()

        fun from(response: Response<*>): ApiException {
            val body = try { response.errorBody()?.string() } catch (_: Throwable) { null }
            val dto = try { body?.let { gson.fromJson(it, ErrorDto::class.java) } } catch (_: Throwable) { null }
            return ApiException(
                dto?.error ?: "http_${response.code()}",
                dto?.message ?: "请求失败 (HTTP ${response.code()})",
                response.code()
            )
        }
    }
}

/** Unwrap a Response: 2xx -> body; otherwise throw ApiException with server machine_code. */
fun <T> Response<T>.unwrap(): T {
    if (isSuccessful) {
        @Suppress("UNCHECKED_CAST")
        return (body() as? T) ?: Unit as T
    }
    throw ApiException.from(this)
}
