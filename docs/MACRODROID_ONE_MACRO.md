# MacroDroidでの使い方

## 結論

現在の安定したIntent受信方式では、MacroDroidは次の2マクロ構成が確実です。

1. 依頼マクロ: Intentを送信して生成を開始
2. 結果マクロ: 生成完了Intentを受信して`answer`をMacroDroid変数へ保存

MacroDroidのTasker/Localeプラグイン出力マッピングが利用できる環境では、プラグインアクション1つのマクロにまとめられる場合があります。ただし端末やMacroDroidのバージョンによって出力変数が渡らないため、Intent送受信の2マクロ方式を推奨します。Taskerではプラグインアクションの出力変数に対応していれば、1タスクで実行できます。

## 事前設定

AI Model Bridge本体で、APIキー・モデル・実行先を設定します。Intentから実行する場合は`backend`を必ず明示してください。

## 依頼マクロ

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
prompt={lv=prompt}
request_id=macro-001
```

ローカルモデルの場合は`backend=local`にします。`prompt`を固定文にする場合は、例えば`prompt=こんにちは`と入力します。

## 結果マクロ

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

