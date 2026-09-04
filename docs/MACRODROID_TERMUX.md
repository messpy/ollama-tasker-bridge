# MacroDroid / Termux連携

このアプリはOllamaサーバーへPullを依頼しません。AndroidがOllama RegistryからGGUFモデルBlobを直接取得し、アプリ内のmodelsディレクトリに保存します。

## Androidへのモデル取得

Action: com.example.ollamataskerbridge.action.PULL

String extra:

- model: llama3.2

取得完了後、RESULT Broadcastを返します。モデル取得には時間と空き容量が必要です。

## Android内モデルで生成

Action: com.example.ollamataskerbridge.action.GENERATE

String extra:

- model: llama3.2
- prompt: 実行するプロンプト
- system_prompt: 任意のシステムプロンプト
- request_id: 任意の呼び出しID
- reply_action: 任意の結果Action
- reply_package: 任意の結果パッケージ

GENERATEは先にPULLしたモデルをAndroid内のllama.cppで実行します。

結果BroadcastのActionは、reply_action指定時はそのAction、未指定時は
com.example.ollamataskerbridge.action.RESULT です。

- Boolean extra ok
- String extra result
- String extra error
- String extra request_id

## Termux例

termux-am broadcast --receiver-foreground -a com.example.ollamataskerbridge.action.PULL --es model llama3.2

termux-am broadcast --receiver-foreground -a com.example.ollamataskerbridge.action.GENERATE --es model llama3.2 --es prompt '今日の予定を3行で要約して'

receiver-foregroundを付けると、完了時の結果BundleをTermux側で確認できます。
