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
| 射撃 | 右クリック(使用) | `semi` は押すたびに1発、`auto` は押している間連射します |
| ロックオン | 右クリック長押し | AA/AT/Missile の武器は、押している間に照準下の目標をロックし、ロック完了後に離すと発射します |
| リロード | Z | 手持ちの弾薬アイテムから装填します。弾切れで撃とうとしたときも自動でリロードします |
| モード切替 | I | 武器ファイルの `ModeNum = 2` の切り替え(機関銃の榴弾、ロケットの子弾、ATミサイルのトップアタック、TVミサイルの自動誘導) |

キーは前提MODと重ならないものを既定にしています。操作設定から変更できます。

## 携帯装備の追加方法

携帯装備は、次の2つのファイルで定義します。

- 武器ファイル: `assets/<namespace>/weapons/<name>.txt`。前提MODの車両用武器と同じ形式で、`Readme_Weapon.md` を参照してください。
- 携帯装備定義: `data/<namespace>/handheld/<name>.json`

どちらも、MODの jar、データパック・リソースパック、`tudursvehiclemod-addons/<pack>/` に置けます。

> 武器ファイル名は全MODで共通の名前として扱われます。他のMODと重ならないよう、接頭辞を付けてください(このMODのサンプルは `tg_`)。

### 携帯装備定義(JSON)

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

## サンプル

| ID | 内容 |
|---|---|
| `tudursguns:sample_rifle` | 30発のフルオート小銃。弾薬は鉄塊(1個で5発) |
| `tudursguns:sample_at_launcher` | 対戦車ミサイル。長押しでロック、TNT1個で1発。モード切替でトップアタック |

クリエイティブタブ「Tudur's Guns」から入手できます。コマンドの場合は次のとおりです。

```
/give @s tudursguns:handheld_weapon[tudursguns:weapon="tudursguns:sample_rifle",tudursguns:ammo=30]
```

サンプルには発射音(`.ogg`)が含まれていないため、発砲は無音です。モデルは箱を組み合わせた仮のものです。

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
  - サンプルの `display` の値は推測です。一人称・三人称・GUI での見え方は、ゲーム内で調整が必要です。
- **クリエイティブタブ**
  - 一覧は、サーバーから定義を受け取った時点の内容で作られます。
  - `/reload` で定義を増やした場合、再接続するまでタブに反映されない可能性があります。
