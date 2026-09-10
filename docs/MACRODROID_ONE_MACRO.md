# MacroDroidでの使い方

## 結論

Tasker Pluginの正式な非同期完了方式に対応したため、MacroDroidは1つのマクロで完結できます。


プラグインアクションの「次のアクションを完了までブロック」をONにし、タイムアウトを120秒にします。完了後に出力変数を次のアクションで使用できます。Taskerではプラグインアクションの出力変数に対応していれば、1タスクで実行できます。

## 事前設定

AI Model Bridge本体で、APIキー・モデル・実行先を設定します。Intentから実行する場合は`backend`を必ず明示してください。

## 互換: Intent送受信（1マクロ方式が使えない場合のみ）

MacroDroidで「アクション」→「Intentを送信」を追加します。

```text
対象: Broadcast
パッケージ: com.messpy.aimodelbridge
Action: com.example.ollamataskerbridge.action.GENERATE
```

String extra:

```text
backend=ollama
model=gpt-oss:120b
prompt=%prompt
request_id=macro-001
```

ローカルモデルの場合は`backend=local`にします。`prompt`を固定文にする場合は、例えば`prompt=こんにちは`と入力します。

## 互換方式の結果受信

別のマクロを作り、トリガーに「Intent受信」を追加します。

```text
Action: com.example.ollamataskerbridge.action.MACRODROID_RESULT
```

受信後のアクションで、IntentのString extra `answer`をMacroDroidのローカル変数`answer`へ保存します。利用できる値は次のとおりです。

```text
answer   生成結果
response 生成結果
result   生成結果
ok       成否（Boolean）
model    使用モデル
error    エラー時の内容
```

`ok`がtrueのときだけ`answer`を通知や次の処理へ渡してください。推論はバックグラウンドのForeground Serviceで実行されるため、すぐに結果が届かない場合があります。

## 変数の注意

MacroDroidの入力変数は`{lv=prompt}`形式です。Taskerは`%prompt`形式です。アプリ側の設定画面では、MacroDroidタブを選んでから保存してください。

