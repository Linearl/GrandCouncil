package com.grandcouncil.remote.agent

import com.grandcouncil.remote.api.HttpClientFactory
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.model.AgentType

/** 按 ConnectionProfile.agentType 创建适配器（新建连接向导第一步选 agent 类型） */
object AgentAdapterFactory {

    fun create(profile: ConnectionProfile): AgentAdapter = when (profile.agentType) {
        AgentType.REASONIX -> ReasonixAdapter(HttpClientFactory.createApi(profile))
        // MiMo Code 为 v2 接入目标（opencode 系 serve 协议，1.6 节已核实同构）
        AgentType.MIMO_CODE -> throw UnsupportedOperationException(
            "MiMo Code 接入为 v2 路线"
        )
    }
}
