package com.musicpipe.player.data.repo

import com.musicpipe.player.data.model.AppUser
import com.musicpipe.player.data.remote.MusicPipeApi
import com.musicpipe.player.data.remote.dto.CreateUserRequest
import com.musicpipe.player.data.remote.dto.UpdateUserRequest
import com.musicpipe.player.data.remote.dto.UserRowDto
import com.musicpipe.player.data.remote.unwrap

/** Admin-only user management (/api/v1/users). Server enforces 403 for role=user. */
class UserRepository(private val api: MusicPipeApi) {

    data class PagedResult(val items: List<UserRowDto>, val total: Int)

    suspend fun list(page: Int = 1, limit: Int = 20): PagedResult {
        val p = api.listUsers(page, limit).unwrap()
        return PagedResult(p.items, p.total)
    }

    suspend fun create(username: String, password: String, role: String): AppUser {
        val dto = api.createUser(CreateUserRequest(username, password, role)).unwrap()
        return AppUser(dto.id, dto.username, dto.role)
    }

    /** PUT /api/v1/users/:id answers {ok:true} only — nothing to return. */
    suspend fun update(id: Long, password: String?, role: String?) {
        api.updateUser(id, UpdateUserRequest(password, role)).unwrap()
    }

    suspend fun delete(id: Long) {
        api.deleteUser(id).unwrap()
    }
}
