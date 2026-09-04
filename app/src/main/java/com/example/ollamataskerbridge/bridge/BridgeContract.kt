package com.example.ollamataskerbridge.bridge

object BridgeContract {
  const val ACTION_GENERATE = "com.example.ollamataskerbridge.action.GENERATE"
  const val ACTION_PULL = "com.example.ollamataskerbridge.action.PULL"
  const val ACTION_RESULT = "com.example.ollamataskerbridge.action.RESULT"

  const val EXTRA_MODEL = "model"
  const val EXTRA_PROMPT = "prompt"
  const val EXTRA_SYSTEM = "system_prompt"
  const val EXTRA_REQUEST_ID = "request_id"
  const val EXTRA_REPLY_ACTION = "reply_action"
  const val EXTRA_REPLY_PACKAGE = "reply_package"
  const val EXTRA_RESULT = "result"
  const val EXTRA_ERROR = "error"
  const val EXTRA_OK = "ok"
}
