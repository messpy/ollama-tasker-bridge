# MacroDroidとのIntent送受信

MacroDroidからアプリへ生成依頼を送り、アプリから結果をBroadcastで返せます。長時間推論はForeground Serviceで実行されます。

## 送信（MacroDroid → アプリ）

MacroDroidの「Intentを送信」アクションで、対象をBroadcast、パッケージを`com.example.ollamataskerbridge`にします。

```text
Action: com.example.ollamataskerbridge.action.GENERATE
Package: com.example.ollamataskerbridge
Extras:
  backend     String  local または ollama
  model       String  qwen3-0.6b-q4_k_m
  prompt      String  {lv=prompt}
  system_prompt String（任意）
  max_tokens  Integer 256
  temperature Float 0.7
  request_id  String（任意）
```

`backend`は必須です。ローカルGGUFを使う場合は`local`、Ollamaサーバーを使う場合は`ollama`を指定します。

## 受信（アプリ → MacroDroid）

MacroDroidの「Intent受信」トリガーを追加し、Actionに次を指定します。

```text
com.example.ollamataskerbridge.action.MACRODROID_RESULT
```

Extrasの保存先を設定します。

```text
answer   → answer
response → response
result   → result
model    → model
ok       → ok
```

`request_id`を指定した場合は、同じExtraで依頼と結果を対応付けられます。
