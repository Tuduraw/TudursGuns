# Tudur's Guns (Fabric 1.21.11)

[Tudur's Vehicle Mod](https://github.com/Tuduraw/tudursvehiclemod)(以下「前提MOD」)のアドオンMODです。銃やミサイルランチャーなどの携帯装備を追加します。

- 武器の性能は、前提MODと同じ武器ファイル(MCHeli形式の `.txt`)で設定します。
- 弾・誘導・命中判定・フレア・発射音・OBJモデル・HUDスクリプトは前提MODの仕組みをそのまま使います。
- 車両に搭乗している間は携帯装備を使えません。

設計の経緯は `docs/DESIGN_DECISION.md` を参照してください。

> **状態: 未検証**
> この環境では Minecraft/Fabric の依存物を取得できないため、まだ一度もビルドしていません。
> 未確認の点は末尾の「未検証の点」にまとめています。

## ビルド

1. 前提MODを `337153f` 以降の状態で、ローカルの Maven に公開します。
   ```
   ./gradlew publishToMavenLocal
   ```
   前提MODの `gradle.properties` の `mod_version` と、このMODの `gradle.properties` の `tudursvehiclemod_version` を一致させてください(現在はどちらも `1.0.3`)。
2. このMODをビルドします。
   ```
   ./gradlew build
   ```

## 操作

| 操作 | 既定のキー | 内容 |
|---|---|---|
| 構える | 左Alt(押している間) | 武器を持ち上げ、照準器を画面中央に合わせます。スコープ付きの武器はスコープを覗き込みます |
| 射撃 | 右クリック(使用) | 構えた状態で撃ちます。武器を下げていれば構えてから撃ち、離すと下げます(構えキーを押している間は構えたまま)。`semi` は押すたびに1発、`auto` は押している間連射します |
| ロックオン | 右クリック長押し | AA/AT/Missile の武器は、押している間に照準下の目標をロックし、ロック完了後に離すと発射します |
| 倍率変更 | マウスホイール | スコープを覗いている間のみ |
| リロード | Z | 手持ちの弾薬アイテムから装填します。弾切れで撃とうとしたときも自動でリロードします |
| モード切替 | I | 武器ファイルの `ModeNum = 2` の切り替え(機関銃の榴弾、ロケットの子弾、ATミサイルのトップアタック、TVミサイルの自動誘導) |

- 構えキーの左Altは、前提MODでも搭乗中の自由視点に使われています。このMODでは搭乗中は構えられないため、実際には競合しません(キーの状態を直接読んでいます)。その他のキーは前提MODと重ならないものを既定にしています。操作設定から変更できます。
- スコープは、構えキーを押しているときだけ覗きます。構えキーを押さずに右クリックで撃つと、覗かずに構えて撃ちます。
- スコープを覗いている間は、視界が倍率に合わせて拡大され、マウス感度も倍率に応じて下がります(望遠鏡と同じ考え方です)。
- 三人称視点と他のプレイヤーからは、構えている間は両手で武器を持ち上げた姿勢(バニラの装填済みクロスボウの姿勢)に見えます。

## アタッチメントと武器作業台

武器作業台(`tudursguns:weapon_workbench`)を右クリックすると、アタッチメントを付け外しする画面が開きます。

- 左の枠に武器を置くと、右に武器のアタッチメント枠(最大4つ)が並び、取り付け済みのアタッチメントが表示されます。
- アタッチメントを枠に入れると取り付け、取り出すと取り外します。
- 各枠に入れられるのは、その武器の定義で指定されたアタッチメントだけです。
- 取り付けたアタッチメントは武器の中に記録されます。画面を閉じると武器だけが手元に戻ります。
- 大型弾倉を外すと、新しい容量を超える装填済みの弾は失われます。

アタッチメントの効果は次のとおりです。

| 効果 | 内容 |
|---|---|
| スコープ | 構えキーで覗き込み、倍率を変えられる |
| 発砲音の変更・音量 | サイレンサーなど。音量を下げると聞こえる距離も短くなる |
| 弾倉容量 | 倍率と加算値 |
| リロード時間 | 倍率 |
| 弾のばらつき | 倍率 |
| 近接攻撃力 | 銃剣など。メインハンドの攻撃力に加算 |

## 携帯装備の追加方法

携帯装備は、次の2つのファイルで定義します。

- 武器ファイル: `assets/<namespace>/weapons/<name>.txt`。前提MODの車両用武器と同じ形式で、`Readme_Weapon.md` を参照してください。
- 携帯装備定義: `data/<namespace>/handheld/<name>.json`

どちらも、MODの jar、データパック・リソースパック、`tudursvehiclemod-addons/<pack>/` に置けます。

> 武器ファイル名は全MODで共通の名前として扱われます。他のMODと重ならないよう、接頭辞を付けてください(このMODのサンプルは `tg_`)。

### 携帯装備定義(JSON)

以下は基本的な項目の例です。構えとアタッチメントの項目は、その後の節で説明します。

```json
{
  "weapon": "tg_sample_rifle",
  "display_name": "Sample Rifle",
  "model": "tudursguns:models/obj/sample_rifle.obj",
  "texture": "tudursguns:textures/handheld/sample_weapons.png",
  "projectile_item": "minecraft:iron_nugget",
  "ammo_item": "minecraft:iron_nugget",
  "rounds_per_ammo_item": 5,
  "fire_mode": "auto",
  "muzzle_offset": [0.25, -0.15, 0.9],
  "inherit_shooter_velocity": false,
  "hud": "my_rifle_hud",
  "display": {
    "firstperson_righthand": { "translation": [0.55, 0.3, 0.3], "rotation": [0, 0, 0], "scale": [0.9, 0.9, 0.9] },
    "gui": { "translation": [0.5, 0.5, 0.5], "rotation": [0, 90, 0], "scale": [0.7, 0.7, 0.7] }
  }
}
```

| キー | 必須 | 既定値 | 内容 |
|---|---|---|---|
| `weapon` | ○ | — | 武器ファイル名(拡張子なし) |
| `display_name` | | 武器ファイルの `DisplayName` | アイテム名 |
| `model` | | なし | OBJモデル(`<namespace>:models/obj/<name>.obj`) |
| `texture` | | なし | テクスチャ(`<namespace>:textures/.../<name>.png`) |
| `projectile_item` | | `minecraft:iron_nugget` | 弾の見た目に使うアイテム。武器ファイルに `ModelBullet` がある場合はそちらが使われます |
| `ammo_item` | | なし(弾薬無制限) | リロードで消費するアイテム。クリエイティブモードでは消費しません |
| `rounds_per_ammo_item` | | `1` | 弾薬アイテム1個あたりの弾数。端数は切り捨てです(使いかけの弾倉を捨てる扱い) |
| `fire_mode` | | `semi` | `semi` または `auto` |
| `muzzle_offset` | | `[0.25, -0.2, 0.8]` | 目の位置から見た発射位置(右, 上, 前)。単位はブロックで、左手のときは左右が反転します |
| `inherit_shooter_velocity` | | `false` | 射手の移動速度を弾に加えるか |
| `hud` | | なし | HUDスクリプト名(下記) |
| `display` | | 変換なし | 表示場所ごとの追加の変換。キーは `firstperson_righthand`, `firstperson_lefthand`, `thirdperson_righthand`, `thirdperson_lefthand`, `gui`, `ground`, `fixed`, `head` |
| `aim` | | なし | 一人称での構えの設定(下記)。ない場合は、一人称でも `display` の値で表示します |
| `attachments` | | なし | アタッチメント枠(下記) |
| `reload_sound` | | なし | リロード開始時の音(`.ogg` のファイル名) |

#### 構え(`aim`)

一人称では、この設定がある武器はカメラ基準の座標で描画されます。

- 下げた状態では `hip_translation` / `hip_rotation` の位置と向きで表示します。
- 構えると、`sight_position` が画面中央に来るよう移動し、その点が目の前 `eye_distance` ブロックの位置に来ます。
- 構えると銃身がそのまま視線方向を向くため、照準器と弾の飛ぶ方向が一致します。

| キー | 既定値 | 内容 |
|---|---|---|
| `sight_position` | `[0, 0.1, 0]` | 照準点(照門の位置など)。モデル座標 |
| `eye_distance` | `0.2` | 構えたときの、目から照準点までの距離 |
| `scale` | `0.5` | 一人称でのモデルの拡大率 |
| `hip_translation` | `[0.3, -0.3, -0.45]` | 下げた状態の位置。カメラ座標で、x は右、y は上、-z は前 |
| `hip_rotation` | `[0, 0, 0]` | 下げた状態の向き(度) |
| `right_arm` / `left_arm` | なし | 腕を表示する位置(`translation` / `rotation` / `scale`)。モデル座標なので、構えの動きに腕も追従します |
| `third_person_aiming` | 三人称の `display` を X 軸で -90 度回したもの | 三人称で構えているときの表示の変換 |

腕の描画には、バニラの一人称の腕の描画(地図を両手で持つときのもの)を使っています。このため、腕の位置の値にはその内部の変換が含まれます。サンプルの値は、地図を持つ姿勢を基準に計算した目安です。

#### アタッチメント枠(`attachments`)

アタッチメントを付ける位置やモデルは、武器側の定義に書きます。同じスコープでも、武器ごとに取り付け位置を変えられます。

```json
"attachments": {
  "optic": {"accepts": {"tudursguns:sniper_scope": {
      "model": "tudursguns:models/obj/sniper_scope.obj",
      "transform": {"translation": [0, 0.155, -0.04]},
      "show_groups": ["scope_mount"],
      "hide_groups": ["iron_sight"]}}},
  "muzzle": {"accepts": {"tudursguns:silencer": {
      "model": "tudursguns:models/obj/silencer.obj",
      "transform": {"translation": [0, 0.038, -1.05]}}}}
}
```

- 枠の名前(`optic` など)は自由です。作業台には名前の順に並び、`gui.tudursguns.slot.<名前>` の翻訳があれば表示に使います。
- `accepts` には、取り付けられるアタッチメントのIDと、その取り付け方を書きます。

| キー | 内容 |
|---|---|
| `model` / `texture` | 別のOBJモデルとして描画する場合。テクスチャの既定値は、アタッチメント定義のテクスチャ、それもなければ武器のテクスチャ |
| `transform` | 武器のモデル座標での位置・回転・拡大 |
| `show_groups` | 武器のOBJのうち、このアタッチメントを付けたときだけ表示するグループ(マウントなど) |
| `hide_groups` | このアタッチメントを付けている間は隠す武器のグループ(スコープを付けたときのアイアンサイト、大型弾倉を付けたときの標準弾倉など) |
| `sight_position` | 付けている間、`aim.sight_position` の代わりに使う照準点 |

すべて省略すると(`{}`)、見た目の変化はなく効果だけが付きます(サンプルのリボルバーのラピッドローダー)。

### アタッチメント定義(`data/<namespace>/attachment/<name>.json`)

```json
{
  "display_name": "Sniper Scope",
  "model": "tudursguns:models/obj/sniper_scope.obj",
  "texture": "tudursguns:textures/handheld/weapons.png",
  "zoom": {"min": 2.0, "max": 10.0, "default": 4.0, "step": 1.0},
  "display": {"gui": {"translation": [0.5, 0.5, 0.5], "rotation": [20, 90, 0], "scale": [1.6, 1.6, 1.6]}}
}
```

| キー | 既定値 | 内容 |
|---|---|---|
| `display_name` | —(必須) | アイテム名 |
| `model` / `texture` / `display` | なし | アイテムとしての見た目(武器の定義と同じ書式) |
| `zoom` | なし | スコープ。`min` / `max` / `default` / `step` で倍率を指定。`overlay` で覗いたときの画像を指定(既定は望遠鏡の画像) |
| `sound_override` | なし | 発砲音を差し替える(`.ogg` のファイル名) |
| `sound_volume_multiplier` / `sound_pitch_multiplier` | `1.0` | 発砲音の音量(聞こえる距離も変わる)・高さの倍率 |
| `magazine_size_multiplier` / `magazine_size_bonus` | `1.0` / `0` | 弾倉容量の倍率・加算値 |
| `reload_time_multiplier` | `1.0` | リロード時間の倍率 |
| `accuracy_multiplier` | `1.0` | 弾のばらつきの倍率 |
| `melee_damage_bonus` | `0` | 近接攻撃力の加算値 |

## クライアント設定(`config/tudursguns-client.json`)

初回起動時に既定値で作成されます。F3+T でも読み直します。

| キー | 既定値 | 内容 |
|---|---|---|
| `aim_offset` | `[0, 0, 0]` | 構えたときの武器の位置の補正。全武器共通、カメラ座標、ブロック単位 |
| `hip_offset` | `[0, 0, 0]` | 下げた状態の位置の補正 |
| `right_arm_offset` / `left_arm_offset` | `[0, 0, 0]` | 腕の位置の補正(モデル座標) |
| `aim_transition_ticks` | `4` | 構え/下げの動きにかかる tick 数 |
| `show_arms` | `true` | 一人称で腕を表示するか |
| `scale_sensitivity_with_zoom` | `true` | スコープの倍率に応じてマウス感度を下げるか |
| `scope_magnification` | `{}` | スコープごとの倍率。ゲーム内で変えると保存され、次回もその倍率で始まります。値を書いておけば初期倍率になります |

個々の武器の位置は武器定義の `aim` で調整し(`/reload` で反映)、見た目の好みに合わせた全体の補正はこの設定で行う想定です。
弾数(`Round`)、リロード時間(`ReloadTime`)、連射間隔(`Delay`)、威力、弾速、ばらつき、ロック時間、発射音などは、すべて武器ファイルから読み込みます。武器ファイルの `MaxAmmo`(予備弾数)は使いません。予備弾はプレイヤーの手持ちの弾薬アイテムです。

### 使用できる武器種別

`MachineGun1/2`, `Rocket`, `Bomb`, `ASMissile`, `MkRocket`, `AAMissile`, `ATMissile`, `Missile`, `TVMissile`, `Dispenser`

`CAS`, `Carrier`, `DropTank`, `Torpedo`, `Depth`, `ASWeapon`, `Smoke`, `TargetingPod`, `Dummy` は車両専用か、弾を撃たない種別のため使えません。

### OBJモデルの向き

- 銃身が -Z 方向、上が +Y、原点がグリップ。1単位が1ブロックです。
- 描画の座標系は、アイテムモデルの 0〜1 の立方体です。`display` の `translation` で位置を合わせてください。

### HUDスクリプト

前提MODのHUDスクリプト(`assets/<namespace>/hud/<name>.txt`、書式は `Readme_HUD.md`)を、`hud` に名前を指定して使えます。使える変数は次のとおりです。

| 変数 | 内容 |
|---|---|
| `ammo` / `max_ammo` | 装填数 / 弾倉の容量 |
| `reserve_ammo` | 手持ちの弾薬で装填できる弾数(無制限のときは -1) |
| `reloading` / `reload_progress` | リロード中か(0/1) / 進行度(0〜1) |
| `mode` / `mode_count` | 選択中のモード(1から数える) / モード数 |
| `lock_tracking` / `locked` / `lock_progress` | 目標を捕捉中か / ロック完了か / 進行度(0〜1) |

文字列変数として `weapon_name` も使えます。`hud` を指定しない場合は、照準の右下に簡易表示が出ます。

## 収録している武器とアタッチメント

| 武器 | 内容 | アタッチメント |
|---|---|---|
| `tudursguns:sniper_rifle` | ボルトアクションの狙撃銃。5発、高威力・高弾速 | スナイパースコープ(2〜10倍)、サイレンサー(発砲音を抑え、音量を1/4に)、大型弾倉(容量2倍、リロード時間1.15倍) |
| `tudursguns:infantry_rifle` | ボルトアクションの歩兵銃。5発 | 銃剣(近接攻撃力 +6) |
| `tudursguns:revolver` | 6発のリボルバー。リロードが遅い | ラピッドローダー(リロード時間 0.3倍) |
| `tudursguns:sample_rifle` | 30発のフルオート小銃 | なし |
| `tudursguns:sample_at_launcher` | 対戦車ミサイル。長押しでロック。モード切替でトップアタック | なし |

弾薬は、サンプルの対戦車ミサイルが TNT、ほかは鉄塊です。1個あたりの弾数は武器ごとに違い、リボルバーは2発、サンプル小銃は5発、ほかは1発です。

- **入手方法**
  - クリエイティブタブ「Tudur's Guns」から入手できます。
  - 狙撃銃・歩兵銃・リボルバー・各アタッチメント・武器作業台には作業台のレシピがあります。
  - コマンドの場合は次のとおりです。
    ```
    /give @s tudursguns:handheld_weapon[tudursguns:weapon="tudursguns:sniper_rifle",tudursguns:ammo=5]
    /give @s tudursguns:attachment[tudursguns:attachment="tudursguns:sniper_scope"]
    ```
- **音**
  - 発砲音・サイレンサー音・リロード音は、このMODで合成したものです(`assets/tudursguns/sounds/`)。
- **弾のモデル**
  - 武器ファイルの `ModelBullet` で指定しています(`models/obj/bullet_<name>.obj` と `textures/vehicle/bullet_<name>.png`)。
- **モデル**
  - どれも箱を組み合わせた仮のものです。

## 未検証の点

ビルドと動作確認は、ローカル環境でお願いします。特に次の点は、この環境で確認できていません。

- **コンパイル全般**
  - Minecraft のクラス名・メソッド名は Yarn `1.21.11+build.4` のマッピングファイルで照合しました。
  - Fabric API の名前は `0.139.4+1.21.11` のソースで照合しました。
  - アクセス修飾子、static かどうか、ジェネリクスは照合できていません。
- **`minecraft:use_effects`**
  - 右クリック長押し中に移動が遅くならないよう、この部品を設定しています。
  - Yarn に名前がないため ID から取得し、`{"can_sprint": true, "speed_multiplier": 1.0}` として読み込んでいます。
  - フィールド名が違う場合は、ログに警告が出て、通常の減速になります。
- **表示位置**
  - 新しい武器・アタッチメントの `display` の値は推測です。
  - 一人称の腕の位置と三人称の構えの向き(`third_person_aiming` の既定値)も推測です。
  - いずれもゲーム内で確認して調整が必要です。
- **Mixin**
  - 対象は次のとおりです。
    - 視野角(`GameRenderer.getFov`)
    - マウスホイール(`Mouse.onMouseScroll`)
    - 視点移動(`Entity.changeLookDirection`)
    - 三人称の姿勢(`LivingEntityRenderer.updateRenderState` / `BipedEntityModel.setAngles`)
    - 一人称の腕(`HeldItemRenderer.renderArm` / `GameRenderer.firstPersonRenderer`)
  - 三人称の姿勢の2つは、前提MODが使っているのと同じ対象・シグネチャです。
- **クリエイティブタブ**
  - 一覧は、サーバーから定義を受け取った時点の内容で作られます。
  - `/reload` で定義を増やした場合、再接続するまでタブに反映されない可能性があります。
