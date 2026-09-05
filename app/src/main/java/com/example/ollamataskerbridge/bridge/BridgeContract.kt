package com.example.ollamataskerbridge.bridge

object BridgeContract {
  const val ACTION_GENERATE = "com.example.ollamataskerbridge.action.GENERATE"
  const val ACTION_PULL = "com.example.ollamataskerbridge.action.PULL"
  const val ACTION_RESULT = "com.example.ollamataskerbridge.action.RESULT"
  const val ACTION_MACRODROID_RESULT = "com.example.ollamataskerbridge.action.MACRODROID_RESULT"
  const val ACTION_LOAD_MODEL = "com.example.ollamataskerbridge.action.LOAD_MODEL"
  const val ACTION_UNLOAD_MODEL = "com.example.ollamataskerbridge.action.UNLOAD_MODEL"
  const val ACTION_DOWNLOAD_MODEL = "com.example.ollamataskerbridge.action.DOWNLOAD_MODEL"
  const val ACTION_LIST_MODELS = "com.example.ollamataskerbridge.action.LIST_MODELS"
  const val ACTION_STOP = "com.example.ollamataskerbridge.action.STOP"

  const val EXTRA_MODEL = "model"
  const val EXTRA_BACKEND = "backend"
  const val EXTRA_MAX_TOKENS = "max_tokens"
  const val EXTRA_TEMPERATURE = "temperature"
  const val EXTRA_PROMPT = "prompt"
  const val EXTRA_SYSTEM = "system_prompt"
  const val EXTRA_REQUEST_ID = "request_id"
  const val EXTRA_REPLY_ACTION = "reply_action"
  const val EXTRA_REPLY_PACKAGE = "reply_package"
  const val EXTRA_RESULT = "result"
  const val EXTRA_ERROR = "error"
  const val EXTRA_OK = "ok"
}
