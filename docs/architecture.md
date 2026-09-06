# アーキテクチャ

```text
Android UI ───────────────┐
                          ├─ DefaultInferenceRepository
Tasker / MacroDroid ──────┘          │
                              ┌─────┴─────┐
                         LocalInference  OllamaClient
                              │                │
                         lib / llama.cpp   Ollama REST API
                              │
                             GGUF
```

通常UIとTasker/MacroDroidは同じ`InferenceRepository`を利用します。自動化の長時間処理は`InferenceForegroundService`で実行し、MacroDroidには`com.example.ollamataskerbridge.action.MACRODROID_RESULT` Broadcastで結果を通知します。

`lib`のJava/Kotlin APIおよびJNI実装はArm AI Chatを基に、このアプリのローカル推論要件に合わせて変更しています。推論バックエンドはportableな静的CPU構成を優先します。
