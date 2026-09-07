package com.grandcouncil.remote.debug

import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.connection.ConnectionStore
import com.grandcouncil.remote.repository.SessionRepository
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Debug-only embedded HTTP API (debugImplementation dependency; binds 127.0.0.1).
 *
 * Built on NanoHTTPD (the standard Android embedded-server library) and exposes
 * the real SessionRepository operations so host-side tooling can drive and
 * observe the app through `adb forward tcp:8770 tcp:8770` instead of fragile
 * UI-coordinate automation:
 *
 *   GET  /api/state                      — profiles snapshot
 *   GET  /api/projects?profile=<id|name> — list projects via /manifest
 *   GET  /api/sessions?project=<projectId>&profile=<id|name> — list sessions
 *   POST /api/refresh                    — re-pull projects for every profile
 *   POST /api/takeover                   — body {"profile","project","sessionId"}
 *
 * All repository calls run with a timeout so a hung serve surfaces as a JSON
 * error instead of a stuck connection.
 */
class DebugApiServer(
    private val store: ConnectionStore,
    private val repository: SessionRepository,
    port: Int = 8770,
) : NanoHTTPD("127.0.0.1", port) {

    override fun serve(session: IHTTPSession): Response = try {
        runBlocking { route(session) }
    } catch (t: Throwable) {
        error(Response.Status.INTERNAL_ERROR, """{"error":${json(t.message ?: t.javaClass.simpleName)}}""")
    }

    private suspend fun route(session: IHTTPSession): Response {
        val uri = session.uri
        val query = session.parameters.mapValues { (_, v) -> v.firstOrNull() ?: "" }
        return when {
            uri == "/api/state" -> handleState()
            uri == "/api/projects" -> handleProjects(query)
            uri == "/api/sessions" -> handleSessions(query)
            uri == "/api/refresh" && session.method == Method.POST -> handleRefresh()
            uri == "/api/takeover" && session.method == Method.POST -> handleTakeover(readBody(session))
            else -> error(Response.Status.NOT_FOUND, """{"error":"unknown path","path":${json(uri)}}""")
        }
    }

    private fun readBody(session: IHTTPSession): String {
        val map = mutableMapOf<String, String>()
        session.parseBody(map)
        return map["postData"] ?: ""
    }

    private suspend fun profiles(): List<ConnectionProfile> =
        withTimeout(20_000) { store.profiles.first() }

    private fun pick(profiles: List<ConnectionProfile>, key: String?): ConnectionProfile? =
        profiles.firstOrNull { it.id == key }
            ?: profiles.firstOrNull { it.name == key }
            ?: profiles.firstOrNull { it.baseUrl.contains(key ?: "\u0000") }
            ?: profiles.firstOrNull()

    private suspend fun handleState(): Response {
        val list = profiles()
        val body = list.joinToString(",", "[", "]") { p ->
            """{"id":${json(p.id)},"name":${json(p.name)},"baseUrl":${json(p.baseUrl)},"authMode":${json(p.authMode.name)},"projectId":${opt(p.projectId)}}"""
        }
        return ok(body)
    }

    private suspend fun handleProjects(query: Map<String, String>): Response {
        val profile = pick(profiles(), query["profile"])
            ?: return error(Response.Status.NOT_FOUND, """{"error":"no profile"}""")
        return withTimeout(25_000) { repository.listProjects(profile) }.fold(
            onSuccess = { projects ->
                ok(projects.joinToString(",", "[", "]") { p ->
                    """{"id":${json(p.id)},"name":${json(p.name)},"state":${json(p.state)},"color":${opt(p.color)},"group":${opt(p.group)}}"""
                })
            },
            onFailure = { error(Response.Status.INTERNAL_ERROR, """{"error":${json(it.message ?: "listProjects failed")}}""") },
        )
    }

    private suspend fun handleSessions(query: Map<String, String>): Response {
        val profile0 = pick(profiles(), query["profile"])
            ?: return error(Response.Status.NOT_FOUND, """{"error":"no profile"}""")
        val projectId = query["project"]
            ?: return error(Response.Status.BAD_REQUEST, """{"error":"project required"}""")
        return withTimeout(30_000) {
            repository.listSessionsForProject(profile0.copy(projectId = projectId), projectId)
        }.fold(
            onSuccess = { sessions ->
                ok(sessions.joinToString(",", "[", "]") { s ->
                    """{"id":${json(s.id)},"title":${json(s.title)},"turns":${s.turns},"current":${s.isCurrent},"heldBy":${opt(s.heldBy?.name)},"path":${json(s.path)}}"""
                })
            },
            onFailure = { error(Response.Status.INTERNAL_ERROR, """{"error":${json(it.message ?: "listSessions failed")}}""") },
        )
    }

    private suspend fun handleRefresh(): Response {
        val list = profiles()
        val parts = mutableListOf<String>()
        for (profile in list) {
            val payload = withTimeout(25_000) { repository.listProjects(profile) }.fold(
                onSuccess = { ps -> """"projects":${ps.size},"state":"ok"}""" },
                onFailure = { e -> """"error":${json(e.message ?: "failed")},"state":"failed"}""" },
            )
            parts.add("""{"profile":${json(profile.name)},$payload""")
        }
        return ok(parts.joinToString(",", "[", "]"))
    }

    private suspend fun handleTakeover(body: String): Response {
        val profileKey = Regex("\"profile\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        val projectId = Regex("\"project\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        val sessionId = Regex("\"sessionId\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        if (profileKey == null || projectId == null || sessionId == null) {
            return error(Response.Status.BAD_REQUEST, """{"error":"profile/project/sessionId required"}""")
        }
        val profile0 = pick(profiles(), profileKey)
            ?: return error(Response.Status.NOT_FOUND, """{"error":"no profile"}""")
        val session = withTimeout(30_000) {
            repository.listSessionsForProject(profile0.copy(projectId = projectId), projectId)
        }.fold(
            onSuccess = { list -> list.firstOrNull { it.id == sessionId } },
            onFailure = { null },
        ) ?: return error(Response.Status.NOT_FOUND, """{"error":"session not found in current list"}""")
        return withTimeout(30_000) {
            repository.takeoverSession(profile0, projectId, session)
        }.fold(
            onSuccess = { ok("""{"ok":true,"session":${json(sessionId)}}""") },
            onFailure = { error(Response.Status.INTERNAL_ERROR, """{"error":${json(it.message ?: "takeover failed")}}""") },
        )
    }

    private fun ok(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)

    private fun error(status: Response.Status, body: String): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body)

    private fun json(v: String) =
        "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun opt(v: String?) = if (v == null) "null" else json(v)
}
