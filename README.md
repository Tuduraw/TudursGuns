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
| 投擲 | 右クリック長押し → 離す | 投擲物を持って押すとピンを抜き、離すと投げます。押している間も信管は進みます(クッキング) |
| 下手投げ | 構えキーを押しながら投げる | 近くへ弱く投げます |
| アンダーバレル切替 | X | 擲弾発射器が付いた武器で、小銃と擲弾発射器を切り替えます |
| 地雷の設置 | 地雷を持ってブロックに右クリック | 車両に貼り付けられるもの(C4)は、しゃがみながら車両に右クリックします |
| 地雷の回収 | しゃがみながら地雷に右クリック | 設置した本人だけ。ほかの人は解除キットを使います |
| 装備の使用 | 右クリック(長押し) | 双眼鏡・応急手当キット・修理キット・解除キット・レーザー目標指示器は長押し、起爆装置は1回押します |

- 構えキーの左Altは、前提MODでも搭乗中の自由視点に使われています。このMODでは搭乗中は構えられないため、実際には競合しません(キーの状態を直接読んでいます)。その他のキーは前提MODと重ならないものを既定にしています。操作設定から変更できます。
- スコープは、構えキーを押しているときだけ覗きます。構えキーを押さずに右クリックで撃つと、覗かずに構えて撃ちます。
- スコープを覗いている間は、視界が倍率に合わせて拡大され、マウス感度も倍率に応じて下がります(望遠鏡と同じ考え方です)。
- 三人称視点と他のプレイヤーからは、構えている間は両手で武器を持ち上げた姿勢(バニラの装填済みクロスボウの姿勢)に見えます。
- **スプリント中は武器を体の前で横向きに持ちます**(一人称では銃口を左下に向けて体の前へ、三人称では両腕を体の前に寄せた持ち方)。
- **構えるとスプリントが解除され、通常の歩行になります。** 構えている間はスプリントを始められません。構えを解くと、スプリントキーを押していれば再びスプリントします。

## アタッチメントと武器作業台

武器作業台(`tudursguns:weapon_workbench`)を右クリックすると、アタッチメントを付け外しする画面が開きます。

- 左の枠に武器を置くと、右に武器のアタッチメント枠(最大4つ)が並び、取り付け済みのアタッチメントが表示されます。
- アタッチメントを枠に入れると取り付け、取り出すと取り外します。
- 各枠に入れられるのは、その武器の定義で指定されたアタッチメントだけです。
- 取り付けたアタッチメントは武器の中に記録されます。画面を閉じると武器だけが手元に戻ります。
- 大型弾倉を外すと、新しい容量を超える装填済みの弾は失われます。
- 擲弾発射器を外すと、発射器に装填していた弾は失われます。

アタッチメントの効果は次のとおりです。

| 効果 | 内容 |
|---|---|
| スコープ | 構えキーで覗き込み、倍率を変えられる |
| 発砲音の変更・音量 | サイレンサーなど。音量を下げると聞こえる距離も短くなる |
| 弾倉容量 | 倍率と加算値 |
| リロード時間 | 倍率 |
| 弾のばらつき | 倍率 |
| 近接攻撃力 | 銃剣など。メインハンドの攻撃力に加算 |
| アンダーバレル | 擲弾発射器など。切替キー(X)で、武器本体と別の武器ファイルの弾を撃ち分ける。弾は本体と別に装填される |

## 投擲物

手榴弾などの投擲物は、1種類のアイテム(`tudursguns:throwable`)に「どの投擲物か」を持たせています。1スタックは16個です。

- **投げ方**
  - 右クリックを押すとピンを抜きます。離すと投げます。
  - 押している間も信管は進みます。クッキングできる投擲物(`cookable`)は、持ちすぎると手元で爆発します。クッキングできないものは、信管の時間は投げた時点から数えます。
  - 構えキー(左Alt)を押しながら投げると、下手投げで近くに転がします。
- **着弾点の表示**
  - 投げる準備をしている間(右クリックを押している間)、前提MODの爆弾の着弾表示と同じように、軌道の線と最初に着地する地点の円を表示します。
  - 軌道は投擲物の武器ファイルの `Gravity` と、前提MODの弾の空気抵抗(1tickごとに0.99倍)で計算しています。跳ねた後の動きは表示しません。
  - 画面には信管の残り時間と、下手投げかどうかを表示します。
- **飛んでいる投擲物**
  - 前提MODの弾(`VehicleProjectileEntity`)として飛びます。爆発は前提MODの爆発なので、車両にも前提MODの武器と同じようにダメージが入ります。
  - 武器ファイルの `Bound` で跳ね返ります。

### 効果

| 種類 | 効果 |
|---|---|
| 爆発(`none`) | 武器ファイルの `Explosion` で爆発します |
| 煙幕(`smoke`) | 範囲に煙の雲を出します。雲の中にいるプレイヤーの画面は灰色になります。**ミサイルのロックオンを吸い寄せます**(下記) |
| 信号(`signal`) | 色付きの煙の柱を上げます。目印用で、ロックオンには影響しません |
| 閃光(`flash`) | 視線が通るプレイヤーの画面を白く飛ばします。近いほど、正面から見ているほど強く長くなります。範囲内のモブは狙いを失い、動きが遅くなります |
| 焼夷(`incendiary`) | 範囲の地面に火をつけ、範囲内の生き物を燃やします |
| 催涙ガス(`gas`) | 煙幕と同じ雲(ロックオンも吸い寄せる)に加えて、中で息をした生き物に吐き気・移動低下・弱体化を与えます。ガスマスクを着けていれば影響を受けません |

**着弾で発動するもの(火炎瓶):** 定義の `impact` を `true` にすると、信管を使わず、最初に何かに当たった時点で効果が出ます。跳ね返らず、クッキングもしません。

**煙幕とロックオン:** 前提MODのロックオンは「照準の方向に最も近い目標」を選びます。煙の雲は、周囲のプレイヤーそれぞれの視線上(雲の中)に見えない目標を置き続けます。雲の方向を狙うと、この目標が照準の真正面になるため、ロックは煙に吸われます。前提MODは変更していません。

- 車両の AA/AT ミサイルにも、このMODの携帯ミサイルにも効きます。
- 雲の外を狙えば、煙の向こうの目標とは関係なく通常どおりロックできます。
- 見えない目標は保存されず、雲が消えると一緒に消えます。

## 弾薬箱

弾薬箱(`tudursguns:ammo_box`)は設置型のブロックです。1秒ごとに次の補給をします。

- 半径4ブロック以内のプレイヤーが持っている、すべての携帯武器の弾倉(擲弾発射器を含む)を満たします。弾薬アイテムは不要です。
  - **武器を下げているときだけ補給します。** 構えている間(構えキー・右クリック)、ロック中、最後の射撃から3秒間は補給しません。車両が止まっているときだけ補給されるのと同じ考え方で、撃ちながら弾が尽きない状態を防ぎます。
- 半径8ブロック以内の止まっている車両に、前提MODの補給車両と同じ補給を1回分します(弾倉の10%、満タンなら予備弾薬の10%)。燃料と修理はしません。

補給の処理は、前提MODの補給(`receiveAmmoSupply`)をそのまま呼んでいます([enemyvehicleaddon](https://github.com/Tuduraw/enemyvehicleaddon) の補給ブロックと同じ方法)。OBJ モデルの補給地点が必要な場合は、前提MODの固定設置物(補給機能付き)で作れます。

## 地雷

地雷・指向性地雷・爆薬は、1種類のアイテム(`tudursguns:mine`)に「どの地雷か」を持たせています。

- **設置:** ブロックに右クリックで置きます。置いてから `arming_ticks` の間は作動しません。
- **見え方と作動:** 全員に見えます。**設置者や仲間の区別はなく、誰にでも作動します**(設置者本人も含む)。
- **爆発:** 前提MODの弾を地雷の位置で爆発させます。対戦車地雷は車両に前提MODの武器と同じダメージを与えます。
- **撃つと爆発:** 作動中の地雷は、撃たれたり、爆発に巻き込まれたり、殴られたりすると爆発します。近くの地雷は連鎖します。作動前なら外れてアイテムに戻ります。クリエイティブモードで殴ると、爆発せずに消えます。
- **回収と解除:** 設置者はしゃがみ+右クリックで回収できます。ほかの人は解除キットを使います。
- **その他:** 下のブロックが壊れると外れてアイテムに戻ります。ワールドに保存されます。

| 種類 | 起爆 |
|---|---|
| 近接(`proximity`) | 範囲内に入った生き物・車両(`trigger_living` / `trigger_vehicles` で選ぶ)。対戦車地雷は車両だけに反応します |
| 指向性(`directional`) | 向いている方向の扇形の範囲に入り、視線が通るもの。破片(別の武器ファイルの弾)を扇状に撃ち出します |
| 遠隔(`remote`) | 起爆装置で、設置した本人が起爆します |

**車両への貼り付け(C4):** `placement` が `anywhere` のものは、しゃがみながら車両に右クリックすると貼り付き、車両と一緒に動きます。車両が消えると外れてアイテムに戻ります。しゃがまずに右クリックすると、前提MODの乗車の操作になります。

## 防具

防具は1種類のアイテム(`tudursguns:armor`)に「どの防具か」を持たせ、バニラの装備部品(`minecraft:equippable`)で装備します。防御力・防具強度・ノックバック耐性・移動速度・耐久値は定義から自動で設定されます。

- **見た目:** バニラと同じ2Dの防具テクスチャ(`equipment_asset`)、OBJ モデル(`model`)、またはその両方。
  - OBJ は、頭・胴・脚のモデルの部位に合わせて描かれ、部位と一緒に動きます(プレイヤー・防具立て・ゾンビなど人型すべて)。
  - OBJ だけのヘルメットは、カボチャと同じくバニラが頭の位置に描きます。位置は定義の `display.head` で決めます。
  - OBJ 防具のモデルは、`worn` でも `display.head` でも **+Z を前** に作ります(バニラの頭の表示は -Z が前なので、このMODが向きを合わせます)。
- **防護:** バニラの防御力の後に、次の軽減が掛かります(着ている防具の分を掛け合わせます)。
  - `ballistic`: 弾や矢などの直撃
  - `blast`: 爆発
  - `headshot`: 頭部への命中で増えるダメージ(ヘルメット)
- **頭部判定:** 弾の軌道が相手の当たり判定の上から22%に当たると頭部とし、ダメージを1.5倍にします(サーバー設定で変更できます)。
- **防弾板の消耗:** `ballistic` を持つ防具は、弾を受けると受けたダメージの分だけ耐久値が減ります(バニラの約4倍)。
- **効果:** 暗視(`night_vision`、ゴーグル。画面が少し緑になります)、ガスの無効化(`gas_protection`)、見つかりにくさ(`detection_multiplier`、ギリースーツ。モブが気付く距離が短くなります)。

## 関連装備

関連装備は1種類のアイテム(`tudursguns:equipment`)に「どの装備か」を持たせています。

| 種類(`type`) | 使い方 |
|---|---|
| 双眼鏡(`binoculars`) | 右クリック長押しか構えキーで覗きます。倍率はホイールで変えます。`rangefinder` を付けると、画面中央の地点までの距離を表示します |
| 応急手当キット(`first_aid`) | 長押しで回復します。見ているプレイヤー(3ブロック以内)がいればその人を、いなければ自分を回復します |
| 車両修理キット(`repair_kit`) | 止まっている車両を見ながら長押しします。前提MODの修理(最大耐久値の2%)を一定間隔で行います。前提MODの補給と同じ処理を呼んでいます |
| 解除キット(`defuse_kit`) | 地雷を見ながら長押しします。地雷ごとの `defuse_ticks` で解除でき、地雷がアイテムで戻ります |
| 起爆装置(`detonator`) | 使うと、自分が設置した遠隔爆薬を範囲内すべて起爆します |
| 地雷探知機(`mine_detector`) | 持っている間、範囲内の地雷を壁越しに枠で表示し、近いほど速く音が鳴ります |
| レーザー目標指示器(`laser_designator`) | 長押しで照射します。照射点は全員に赤い点で見え、**ミサイルでロックオンできます**(車両の武器もこのMODの携帯ミサイルも)。建物や地点のように、生き物や車両でないものを狙わせるのに使います |

レーザー目標指示器は、煙幕と同じ方法(見えない目標を照射点に置く)で、前提MODを変更せずに実装しています。

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
| `third_person_aiming` | 三人称の `display` と同じ | 三人称で構えているときの表示の変換。構えた姿勢で向きがずれるモデルの場合に指定します |
| `sprint_translation` | `[0.1, -0.36, -0.42]` | 一人称のスプリント中の位置(`hip_translation` と同じ空間) |
| `sprint_rotation` | `[-20, 60, 20]` | 一人称のスプリント中の回転(度)。既定は銃口を左下へ向けた横向き |
| `third_person_sprinting` | 三人称の `display` と同じ | 三人称でスプリント中の表示の変換。腕は定義によらず体の前に寄せた持ち方になります |

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

### 投擲物定義(`data/<namespace>/throwable/<name>.json`)

アドオンフォルダ(`tudursvehiclemod-addons/<pack>/data/<namespace>/throwable/`)にも置けます。飛ぶ物の性能(威力・爆発・重力・跳ね返り・音・弾のモデル)は武器ファイルで設定し、このファイルでは投げ方と爆発以外の効果を設定します。

| キー | 既定値 | 内容 |
|---|---|---|
| `weapon` | (必須) | 武器ファイル名 |
| `display_name` | 武器ファイルの `DisplayName` | 表示名 |
| `model` / `texture` | なし | 手に持ったとき・飛んでいるときの OBJ モデルとテクスチャ。ない場合は平面のアイコンで表示します |
| `display` | なし | 表示位置ごとの変形(携帯装備定義と同じ形式) |
| `throw_velocity` | `1.2` | 投げる速さ(ブロック/tick) |
| `underhand_velocity` | `0.5` | 下手投げの速さ |
| `fuse_ticks` | `80` | ピンを抜いてから爆発・発動するまでの時間 |
| `cookable` | `true` | `true` はピンを抜いた時点から信管が進みます。`false` は投げた時点から進みます |
| `effect` | なし | 爆発以外の効果。`type`(`smoke` / `signal` / `flash` / `incendiary`)、`radius`、`duration_ticks`、`color`(`[r, g, b]`、各0〜1) |
| `pin_sound` | なし | ピンを抜く音(武器ファイルの `Sound` と同じ指定方法) |

- 武器ファイルには `Explosion` と `Gravity`、跳ねさせる場合は `Bound` を書きます。煙などの効果だけの投擲物は `Explosion = 0` にします。
- 武器ファイルの `DelayFuse` を書かなければ、着弾しても爆発せず、`fuse_ticks` で発動します。書いた場合は着弾からその時間で発動します。
- 武器ファイルの `Sound` は投げたときの音になります。

- 定義の `impact` を `true` にすると、着弾で発動します(火炎瓶)。`fuse_ticks` は、どこにも当たらなかった場合の保険になります。
- `effect.sound` は、効果が出た場所で鳴らす音です。

### 地雷定義(`data/<namespace>/mine/<name>.json`)

| キー | 既定値 | 内容 |
|---|---|---|
| `weapon` | (必須) | 爆発の武器ファイル名(`Explosion`・`Power`・`ExplosionBlock` など) |
| `display_name` | 武器ファイルの `DisplayName` | 表示名 |
| `model` / `texture` / `display` | なし | アイテムと設置した地雷の OBJ モデル |
| `placed` | なし | 設置したときのモデルの変形。モデルの +Y が設置面の外向き、+Z が正面(指向性地雷が撃つ方向) |
| `trigger` | `proximity` | `proximity` / `directional` / `remote` |
| `trigger_radius` | `1.0` | 反応する距離 |
| `trigger_living` / `trigger_vehicles` | `true` / `true` | 生き物・車両に反応するか |
| `arming_ticks` | `60` | 設置から作動までの時間 |
| `trigger_delay_ticks` | `0` | 反応してから爆発するまでの時間 |
| `placement` | `ground` | `ground`(地面の上)/ `surface`(どの面にも)/ `anywhere`(どの面にも、車両にも) |
| `fragments` | なし | `weapon`(破片の武器ファイル)、`count`、`spread_degrees`(扇の角度。指向性地雷の反応範囲にもなる) |
| `defuse_ticks` | `60` | 解除キットでの解除にかかる時間 |
| `sounds` | なし | `place`(設置)・`arm`(作動)・`trigger`(反応) |

### 防具定義(`data/<namespace>/armor/<name>.json`)

| キー | 既定値 | 内容 |
|---|---|---|
| `slot` | (必須) | `head` / `chest` / `legs` / `feet` |
| `display_name` | ファイル名 | 表示名 |
| `armor` / `toughness` / `knockback_resistance` | `0` | バニラと同じ |
| `movement_speed` | `0` | 移動速度の増減の割合(`-0.15` で15%遅く) |
| `durability` | `0` | 耐久値。`0` は壊れない |
| `protection` | なし | `ballistic` / `blast` / `headshot`(それぞれ 0〜1) |
| `equipment_asset` | なし | 2Dの見た目。`assets/<ns>/equipment/<name>.json` と `textures/entity/equipment/humanoid/<name>.png`(脚は `humanoid_leggings`)。バニラの防具と同じ形式 |
| `item_model` | なし | アイテムのモデル(`assets/<ns>/items/<name>.json`)。ない場合は OBJ モデルをアイテムとして描きます |
| `model` / `texture` / `display` | なし | OBJ の見た目 |
| `worn` | なし | OBJ を着たときの位置。部位の空間で、原点は部位の付け根(頭と胴は首、脚は股関節)、+Y が上、+Z が前、1 = 1ブロック |
| `effects` | なし | `night_vision`、`gas_protection`、`detection_multiplier` |

### 関連装備定義(`data/<namespace>/equipment/<name>.json`)

| キー | 既定値 | 内容 |
|---|---|---|
| `type` | (必須) | 上の表の種類 |
| `display_name` | ファイル名 | 表示名 |
| `model` / `texture` / `display` | なし | OBJ の見た目 |
| `max_stack` | `1` | 重ねられる数(`uses` がないとき) |
| `uses` | `0` | 使用回数(耐久値)。`0` は無制限。応急手当キットは `0` のとき1回で1個消費 |
| `use_ticks` | `40` | 応急手当キットの使用時間、修理キットの修理間隔 |
| `range` | 種類ごと | 届く距離(応急手当・解除3、修理5、探知12、起爆256、照射・双眼鏡512) |
| `zoom` | なし | 双眼鏡の倍率(スコープと同じ形式) |
| `rangefinder` | `false` | 距離を表示するか |
| `heal` / `regeneration_ticks` | `8` / `0` | 応急手当キットの回復量と、再生効果の時間 |
| `repair_steps` | `1` | 修理キットが1回に行う修理の回数 |
| `sound` | なし | 使ったときの音 |

### アタッチメントの `underbarrel`

アタッチメント定義に `underbarrel` を書くと、武器の下に付く別の武器になります。

| キー | 既定値 | 内容 |
|---|---|---|
| `weapon` | (必須) | 武器ファイル名(弾数は `Round`、リロードは `ReloadTime`) |
| `projectile_item` | `minecraft:iron_nugget` | 弾の見た目のアイテム |
| `ammo_item` / `rounds_per_ammo_item` | なし / `1` | 弾薬 |
| `reload_sound` | なし | リロードの音 |
| `muzzle_offset` | 武器本体の値 | 発射位置 |

### 持っているときの移動速度(全種類共通)

携帯装備・アタッチメント・投擲物・地雷・関連装備の定義には、次のキーを書けます。**書かなければ速度は変わりません。**

| キー | 既定値 | 内容 |
|---|---|---|
| `movement_speed` | `0` | 手に持っている間の移動速度の増減(割合)。`-0.1` で10%遅く、`0.2` で20%速く |
| `aiming_movement_speed` | `0` | 構えている間に、さらに加える増減(携帯装備と、その武器に付けたアタッチメントだけ) |

- 防具の `movement_speed` と同じ仕組み(移動速度の属性に、割合で掛かる修正)です。
- 両手に持っている物と、武器に付けたアタッチメントの値を足し合わせます。合計が `-1` を下回ることはありません(止まるまで)。
- 例: 重い武器に `"movement_speed": -0.15`、軽量化ストックのアタッチメントに `"movement_speed": 0.05`、狙撃銃に `"aiming_movement_speed": -0.3`。
- サンプルでは、対戦車ミサイル(持っている間 -10%、構えるとさらに -20%)と狙撃銃(構えている間 -25%)に設定しています。
- アイテムの説明に増減が表示されます。

## サーバー設定(`config/tudursguns-server.json`)

| キー | 既定値 | 内容 |
|---|---|---|
| `headshot_multiplier` | `1.5` | 頭部に当たったときのダメージ倍率(`1` で頭部判定なし) |
| `headshots_players_only` | `false` | 頭部判定をプレイヤーだけにするか |

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

| 投擲物 | 内容 |
|---|---|
| `tudursguns:frag_grenade` | 破片手榴弾。4秒で爆発。地形は壊しません |
| `tudursguns:smoke_grenade` | 発煙手榴弾。半径5ブロックの煙を30秒 |
| `tudursguns:flash_grenade` | 閃光手榴弾。1.5秒で発動。半径16ブロック |
| `tudursguns:incendiary_grenade` | 焼夷手榴弾。半径3ブロックに火をつける |
| `tudursguns:signal_grenade` | 信号弾。赤い煙を60秒 |
| `tudursguns:molotov` | 火炎瓶。当たった場所で半径3ブロックに火をつける |
| `tudursguns:tear_gas_grenade` | 催涙ガス手榴弾。半径5ブロックのガスを25秒 |

| 地雷 | 内容 |
|---|---|
| `tudursguns:ap_mine` | 対人地雷。踏むとカチッと鳴って0.3秒後に爆発。生き物と車両に反応 |
| `tudursguns:at_mine` | 対戦車地雷。車両にだけ反応。大きな爆発 |
| `tudursguns:claymore` | 指向性地雷。前方5ブロック・60度の範囲に入ると、破片40発を扇状に撃ち出す |
| `tudursguns:c4` | 遠隔爆薬。どの面にも、車両にも貼れる。起爆装置で起爆 |

| 防具 | 内容 |
|---|---|
| `tudursguns:combat_helmet` | 戦闘用ヘルメット。頭部保護60%(2D) |
| `tudursguns:nvg_helmet` | 暗視ゴーグル付きヘルメット。2Dのヘルメットに OBJ のゴーグル |
| `tudursguns:gas_mask` | ガスマスク。OBJ のみ |
| `tudursguns:plate_carrier` | プレートキャリア。防弾35%・爆風20% |
| `tudursguns:heavy_armor` | 重装甲。防弾55%・爆風35%、移動15%低下 |
| `tudursguns:ghillie_suit` | ギリースーツ。モブが気付く距離が35%に |

| 関連装備 | 内容 |
|---|---|
| `tudursguns:binoculars` / `tudursguns:rangefinder` | 双眼鏡(2〜8倍)/ 測距儀付き(4〜12倍) |
| `tudursguns:first_aid_kit` | 応急手当キット。2秒で4ハート+再生5秒 |
| `tudursguns:repair_kit` | 車両修理キット。1秒ごとに2%修理、64回 |
| `tudursguns:defuse_kit` | 解除キット。16回 |
| `tudursguns:detonator` | 起爆装置 |
| `tudursguns:mine_detector` | 地雷探知機。12ブロック |
| `tudursguns:laser_designator` | レーザー目標指示器。512ブロック |
| `tudursguns:underbarrel_launcher` | 擲弾発射器(アタッチメント)。歩兵銃に付けられる。弾はファイアチャージ |

弾薬は、サンプルの対戦車ミサイルが TNT、ほかは鉄塊です。1個あたりの弾数は武器ごとに違い、リボルバーは2発、サンプル小銃は5発、ほかは1発です。

- **入手方法**
  - クリエイティブタブ「Tudur's Guns」から入手できます。
  - 狙撃銃・歩兵銃・リボルバー・各アタッチメント・武器作業台・各投擲物・弾薬箱・各地雷・各防具・各関連装備には作業台のレシピがあります。
  - コマンドの場合は次のとおりです。
    ```
    /give @s tudursguns:handheld_weapon[tudursguns:weapon="tudursguns:sniper_rifle",tudursguns:ammo=5]
    /give @s tudursguns:attachment[tudursguns:attachment="tudursguns:sniper_scope"]
    /give @s tudursguns:throwable[tudursguns:throwable="tudursguns:frag_grenade"] 16
    /give @s tudursguns:mine[tudursguns:mine="tudursguns:ap_mine"] 8
    /give @s tudursguns:armor[tudursguns:armor="tudursguns:plate_carrier"]
    /give @s tudursguns:equipment[tudursguns:equipment="tudursguns:detonator"]
    ```
  - 防具と関連装備は、コマンドで出した直後はまだ装備部品などが付いていません。インベントリに入った次の tick にサーバーが付けます。
- **音**
  - 音はすべてこのMODで合成したものです(`assets/tudursguns/sounds/`)。
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
  - 一人称の腕の位置も推測です。
  - いずれもゲーム内で確認して調整が必要です。
- **Mixin**
  - 対象は次のとおりです。
    - 視野角(`GameRenderer.getFov`)
    - マウスホイール(`Mouse.onMouseScroll`)
    - 視点移動(`Entity.changeLookDirection`)
    - 三人称の姿勢(`LivingEntityRenderer.updateRenderState` / `BipedEntityModel.setAngles`)
    - 一人称の腕(`HeldItemRenderer.renderArm` / `GameRenderer.firstPersonRenderer`)
    - 一人称の基準の姿勢(`HeldItemRenderer.renderItem`。前提MODと同じ対象・シグネチャ)
  - 三人称の姿勢の2つは、前提MODが使っているのと同じ対象・シグネチャです。
- **クリエイティブタブ**
  - 一覧は、サーバーから定義を受け取った時点の内容で作られます。
  - `/reload` で定義を増やした場合、再接続するまでタブに反映されない可能性があります。
- **投擲物**
  - 効果(煙・閃光・焼夷)は、飛んでいる弾がワールドから取り除かれた理由で判定しています。信管や着弾で消えたとき(`DISCARDED` / `KILLED`)だけ発動し、チャンクの読み込み解除では発動しません。前提MODが別の理由で弾を消している場合は発動しない可能性があります。
  - 着弾点の表示の軌道が、実際の弾の動きと一致するかは未確認です。
  - 跳ね返りを繰り返した後、地面で止まるかどうかは前提MODの `Bound` の動作次第です。
- **煙幕のロックオン吸収**
  - 見えない目標(`LivingEntity`)を置く方法が、前提MODのロックオン探索で期待どおり選ばれるかは未確認です。
  - 敵のモブがこの目標を攻撃対象にしないかも確認が必要です。
- **弾薬箱**
  - 前提MODの private メソッドを Mixin の `@Invoker` で呼んでいます。前提MODでメソッド名が変わると起動時にエラーになります。
- **地雷**
  - 地雷は `Entity` で、当たり判定を持たせて撃てるようにしています。前提MODの弾が地雷に当たるか(弾の当たり判定の対象になるか)は未確認です。
  - 地雷の爆発は、`TimeFuse = 0` の前提MODの弾をその場に出して起こしています。1tick後に爆発する想定です。
  - 車両に貼り付けた C4 は、車両の向き(ヨー)にだけ追従します。車両が傾いてもずれません(ピッチ・ロールには追従しません)。
  - しゃがみ+右クリックで車両に貼るとき、前提MODの車両がしゃがみ中の右クリックを受け付けない前提です。
- **防具**
  - 装備部品などは、サーバーがインベントリの tick で付けています。クリエイティブタブから出したものは最初から付いています。
  - OBJ 防具の位置(`worn`、`display.head`)と、2Dテクスチャの見え方は未確認です。
  - 頭部判定は、弾の現在位置と速度から命中点を推定しています。
- **関連装備**
  - 双眼鏡の倍率表示・距離表示の位置、マスク画像の見え方は未確認です。
  - レーザーの照射点へのロックは、煙幕と同じ仕組みです。
- **アンダーバレル**
  - 擲弾発射器の選択中の HUD は、リロードの進み具合を本体の `ReloadTime` で計算しています(表示のみ)。
