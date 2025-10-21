package com.rj.islamove.data.api

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class RenderApiService(
    private val baseUrl: String,
    private val client: OkHttpClient
) {
    fun deleteUser(userId: String, adminId: String, token: String, callback: (Boolean, String?) -> Unit) {
        val json = JSONObject().apply {
            put("uid", userId)
            put("adminId", adminId)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/api/users/$userId")
            .delete(body)  // DELETE with body
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                callback(response.isSuccessful, response.body?.string())
            }
        })
    }

    fun updateUserPassword(
        userId: String,
        newPassword: String,
        adminId: String,
        token: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val json = JSONObject().apply {
            put("uid", userId)
            put("newPassword", newPassword)
            put("adminId", adminId)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/api/users/$userId/password")
            .put(body)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                callback(response.isSuccessful, response.body?.string())
            }
        })
    }
}