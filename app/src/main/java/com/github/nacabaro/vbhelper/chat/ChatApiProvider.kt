package com.github.nacabaro.vbhelper.chat

/** Known OpenAI Chat Completions-compatible gateways. */
enum class ChatApiProvider(
    val displayName: String,
    val baseUrl: String?,
    val suggestedModel: String? = null
) {
    OPENROUTER(
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1/",
        suggestedModel = "openrouter/auto"
    ),
    AIRFORCE(
        displayName = "Api.Airforce",
        baseUrl = "https://api.airforce/v1/",
        suggestedModel = "gpt-4.1-mini"
    ),
    UNO_ROUTER(
        displayName = "UnoRouter",
        baseUrl = "https://api.unorouter.com/v1/",
        suggestedModel = "gpt-oss-120b:free"
    ),
    UNITEROUTER(
        displayName = "UniteRouter",
        baseUrl = "https://api.uniterouter.ai/v1/"
    ),
    OPENCODE_ZEN(
        displayName = "OpenCode Zen",
        baseUrl = "https://opencode.ai/zen/v1/",
        // This model uses OpenCode Zen's documented Chat Completions endpoint.
        suggestedModel = "minimax-m2.7"
    ),
    LITELLM(
        displayName = "LiteLLM (seu proxy)",
        baseUrl = null
    ),
    CUSTOM(
        displayName = "Manual (compatível com OpenAI)",
        baseUrl = null
    );

    companion object {
        fun fromBaseUrl(baseUrl: String): ChatApiProvider = entries.firstOrNull {
            it.baseUrl != null && it.baseUrl.equals(baseUrl.trimEnd('/') + "/", ignoreCase = true)
        } ?: CUSTOM
    }
}
