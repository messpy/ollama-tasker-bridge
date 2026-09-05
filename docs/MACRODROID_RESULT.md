# MacroDroidで生成結果を受け取る

Locale/Taskerプラグインのアクションは推論を実行します。生成完了時には、次の専用Broadcastも送信します。

```text
Action: com.example.ollamataskerbridge.action.MACRODROID_RESULT
```

MacroDroidでは「Intent受信」トリガーを追加し、上記Actionを指定してください。受信データは次のExtrasです。

```text
ok       Boolean
result   String
response String
answer   String
model    String
```

MacroDroidのトリガー側で、受信した`answer`または`response`をローカル変数へ保存します。既存のプラグインアクションにある「MacroDroid変数名（結果）」は、従来のOrdered Broadcast向け設定としても利用できます。
