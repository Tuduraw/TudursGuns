# HMG EX パック変換ツール

[Hand Made Guns & Vehicle : EX](https://forum.civa.jp/viewtopic.php?t=24)(Minecraft 1.7.10)向けに作られたパックを、Tudur's Guns で読み込める形に変換します。

- 対象は HMG EX 12.x のパック形式です(`guns/`、`attachment/`、`bullets/`、`addpackrecipe/`、`assets/`)。
- 変換結果は推定を含みます。特に表示位置・照準位置・腕の位置は、ゲーム内で確認して調整してください。

## 必要なもの

Python 3.8 以降。標準ライブラリだけで動きます。

## 使い方

```
python hmgex_convert.py <パックのフォルダ または .zip> -o <出力先>
```

例:

```
python hmgex_convert.py HMG_MyPack.zip -o converted
```

## 出力

```
<出力先>/
├─ tudursvehiclemod-addons/<パック名>/   … ゲームフォルダの tudursvehiclemod-addons/ にフォルダごと置く
│   ├─ assets/<名前空間>/weapons/          武器ファイル(.txt)
│   ├─ assets/<名前空間>/models/obj/       模型(向きと大きさを変換済み)
│   ├─ assets/<名前空間>/textures/vehicle/ 模型のテクスチャ、アイコン(icons/)、スコープ画像(scopes/)
│   ├─ assets/<名前空間>/sounds/           パックに含まれていた音
│   ├─ data/<名前空間>/handheld/, attachment/, ammo/   銃・アタッチメント・マガジンの定義
│   └─ data/<名前空間>/gun_recipe/          銃器製作台のレシピ
├─ datapacks/<パック名>/                  … (--datapack-recipes のときだけ)普通の作業台のレシピ。ワールドの datapacks/ に置く
└─ conversion_report.md                  … ファイルごとの変換結果(変換・近似・未対応・注意)
```

- 名前空間は、パック名を小文字にしたものです(`--namespace` で変更可)。
- アイテムは Tudur's Guns の共通アイテム(銃・アタッチメント・弾薬)として追加されます。クリエイティブタブ「Tudur's Guns」に並びます。
- レシピは銃器製作台(`tudursguns:gun_crafting_table`)で使えます。アドオンのフォルダに入っているため、ワールドごとのデータパックは不要です。
- 普通の作業台でも作れるようにしたい場合は `--datapack-recipes` を付けます。データパックの `pack_format` は既定で 94 です。ゲームのバージョンに合わない場合は `--pack-format` で指定してください。

## オプション

| オプション | 既定値 | 内容 |
|---|---|---|
| `--namespace` | パック名 | 出力の名前空間 |
| `--scale-factor` | `0.16` | 模型の大きさ。`ModelScala` にこれを掛けてブロック単位にします(小銃がおよそ1ブロックになる値) |
| `--speed-scale` | `1.0` | `BulletSpeed` に掛ける値 |
| `--gravity-scale` | `0.03` | `BulletGravity` に掛ける値 |
| `--spread-scale` | `1.0` | `BulletSpread` に掛ける値 |
| `--recoil-scale` | `1.0` | `Recoil` に掛ける値(結果は視点の跳ね上がりの角度) |
| `--recoil-ticks` | `4` | リコイルのモーションキー(0〜10)を何 tick に当てるか |
| `--cock-delay` | `2` | 射撃からコッキングの動きを始めるまでの tick |
| `--default-ammo` | `minecraft:iron_nugget` | パック内にマガジンがない銃の弾薬アイテム |
| `--default-launcher-ammo` | `minecraft:fire_charge` | 同じく、ロケット・グレネードランチャーの弾薬 |
| `--no-ammo` | — | パック内にマガジンがない銃を、弾薬なし(無限)にする |
| `--sound-map` | — | HMG の音の名前 → Tudur's Guns の音の名前 の対応表(JSON。下記) |
| `--datapack-recipes` | — | 銃器製作台のレシピに加えて、普通の作業台用のレシピをデータパックとしても出力する |
| `--pack-format` | `94` | `--datapack-recipes` のデータパックの `pack_format` |

### 音

HMG の標準の音(`handmadeguns.fireRifle` など)は HMG 本体に入っているため、パックには含まれていません。これらは Tudur's Guns の音に置き換えます。

| HMG の音 | 置き換え先 |
|---|---|
| `fire` | `tg_sample_rifle` |
| `fireRifle` | `tg_rifle_shot` |
| `firehg` | `tg_revolver_shot` |
| `HeavyRifle` | `tg_sniper_shot` |
| `supu` | `tg_suppressed_shot` |
| `reload` | `tg_reload_magazine` |
| `cooking`、`a_cock` | `tg_reload_bolt` |
| `null` | なし |

- パックの `assets/<ドメイン>/sounds.json` にある音は、ファイルをコピーしてそのまま使います。
- 対応表を変えたい・増やしたい場合は、次のような JSON を `--sound-map` で渡します。値に `null` を書くと音なしになります。

```json
{"fireRifle": "my_rifle_shot", "reload": "tg_reload_bolt"}
```

## 変換の内容

### 銃(`guns/*.txt`)

| HMG | Tudur's Guns |
|---|---|
| 登録行(`HG`/`AR`/`SR`/`RL` など) | 銃(`data/<ns>/handheld/<内部名>.json`)。`RL`・`GL` はロケット弾、それ以外は銃弾 |
| `Name` | 表示名(なければ内部名) |
| `BulletPower`、`BulletSpeed`、`BulletGravity`、`BulletSpread`、`Explosion`、`BlockDestory`、`bulletFuse` | 武器ファイルの `Power`、`Acceleration`、`Gravity`、`Accuracy`、`Explosion`、`ExplosionBlock`、`TimeFuse` |
| `RemainingBullet`、`ReloadTime` | `Round`、`ReloadTime` |
| `Cycle`、`CockingTime` | `Delay`(大きい方) |
| `Automatic`、`Bursts` | `fire_mode`(`auto` / `semi` / `burst`) |
| `PerFireRound` | `pellets`(散弾) |
| `Recoil`、`Recoil_sneaking` | `recoil`、`recoil_sneaking`(視点の跳ね上がり) |
| `ADS_Spread_coefficient` | `ads_spread_multiplier`(構えキーで構えたときの拡散) |
| `Attacking` | `melee_damage` |
| `Motion` | `movement_speed`(`Motion - 1`) |
| `Zoom`(1つ目) | 構え時の拡大(`aim.zoom`)。`ScopeTexture` もあれば内蔵スコープ(`aim.scope`) |
| `SprintingRotation` | スプリント時の回転 |
| `Texture` | インベントリのアイコン |
| `GunSound`(1つ目・2つ目)、`GunSoundReload`、`GunSoundCooking` | 発砲音、サプレッサー装着時の音、リロード音、コッキング音(射撃の動作の中で鳴らす) |
| `Magazine`、`MultiMagazine` | パック内のマガジンなら、その弾薬アイテム。なければ `--default-ammo` |
| `Canlock`、`Induction_precision` | 対戦車ミサイル(ロックオン)、旋回性能 |
| `MuzzleFlash` | マズルフラッシュ(`false` なら `effects.muzzle_flash: false`) |
| `Cartridge`、`CartridgeType`、`CartCount`、`DropCartridgeEndCocked`、`BulletNameCart` | 薬莢の排出(`effects.cartridge`)。種類は収録の模型(1 小銃、2 拳銃、3 散弾、4 擲弾、5 マガジン)、`BulletNameCart` があればその模型。コッキング終了時の排莢は `CockingTime` 後に出す |
| `DropMagazine`、`MagType`、`MagCount`、`BulletNameMAG` | リロード時のマガジンの排出(`effects.magazine`)。種類の番号は `CartridgeType` と同じ |
| `ObjModel`、`ObjTexture`、`ModelScala` | 模型とテクスチャ |
| `BulletNameNormal`、`BulletNameALL` | 弾の模型(`bullets/` の定義から) |

### パーツとモーション

HMG のパーツレンダー(`AddParts`)を、Tudur's Guns のモーション(`animation`)に変換します。

| HMG | Tudur's Guns |
|---|---|
| `AddParts`、`AddChildParts`、`BackParts` | 部品と親子関係 |
| `AddPartsRotationCenterAndRotationAmount` | 回転の中心 |
| `AddPartsRotationDefOffset` | 部品の常時の位置 |
| `AddPartsOnADSOffsetAndRotation` | 構えたときの位置 |
| `AddRecoilMotionKey`、`AddPartsOnRecoilOffsetAndRotation` | 射撃時の動き |
| `AddCockMotionKey`、`AddPartsOnCockOffsetAndRotation` | 射撃後のコッキングの動き |
| `AddReloadMotionKey`、`AddPartsOnReloadOffsetAndRotation` | リロードの動き(キーがなければリロード時間に合わせて伸縮) |
| `AddPartsRenderAsBulletInf` | 1発ごとに進んで残る動き(リボルバーのシリンダーなど) |
| `AddBulletPositions` | 装填数に応じた位置 |
| `MuzzleJump` | 射撃時に銃全体が跳ねる動き |
| パーツの `scope`/`dot`/`sight`/`grip`/`gripcover`/`muzzlepart` などの印 | アタッチメント装着時の表示・非表示 |
| `turretbase`、`underOnly` の印 | 模型から除外(設置・アンダーバレルは未対応のため) |

### アタッチメント(`attachment/*.txt`)

| HMG | Tudur's Guns |
|---|---|
| `SCOPE`、`RedDot`、`Model_Sight` | スコープ(`Zoom`、`ScopeTexture`) |
| `Suppressor` | 発砲音の変更と音量 0.3倍、マズルフラッシュなし。銃の `GunSound` の2つ目があれば、その銃ではその音 |
| `Grip`、`Model_Grip` | 拡散(`AntiBure`)とリコイル(`AntiRecoil`)の倍率 |
| `Laser`、`Light` | 見た目のみ |
| `Magazine`、`CustomMagazine` | 弾薬アイテム(`data/<ns>/ammo/`)。`BulletRound` が1個あたりの弾数 |
| 銃の `SightSetPoint`、`MuzzleSetPoint`、`GripSetPoint`、`LightSetPoint` | 取り付け位置 |
| 銃の `attachRestriction`、`allowattach` | 取り付けられるアタッチメント(制限なしなら、同じ種類のものすべて) |

### レシピ(`addpackrecipe/*.txt`)

- 銃器製作台のレシピ(`gun_recipe`)にします。`Slot1`〜`Slot9` の材料を種類ごとにまとめて数えます(並びは問いません)。
- `--datapack-recipes` のときは、3×3の並びを、形の決まった作業台のレシピにもします。空いた行・列は詰めます。
- 1.7.10 の名前が変わったもの(`planks`、`log`、`wool` など)は、1.21 の名前やタグに置き換えます。メタデータ(色違いなど)は無視します。
- 材料が変換した銃・アタッチメント・マガジンのときは、そのアイテムだけに一致する材料にします(`gun_recipe` の `weapon`・`attachment`・`ammo`。作業台のレシピでは Fabric API の `fabric:components`)。
- パックの外のアイテム(HMG 本体のマガジンなど)を使うレシピは省略します。

## 変換できないもの

レポート(`conversion_report.md`)に、ファイルごとに理由付きで一覧されます。主なものは次のとおりです。

- **位置合わせ:** 一人称・三人称・構えたときの位置(`ModelEquipped`、`ModelHigh`、`ModelRotation*`、腕の位置など)は、HMG と表示の仕組みが違うため変換せず、模型の大きさと Tudur's Guns のサンプル銃の値から計算します。
- **弾の挙動:** 跳弾、ノックバック、可変拡散。
- **設置とアンダーバレル:** 依託射撃・設置(タレット)、アンダーバレルへの銃の取り付け。
- **見た目の演出:** マズルフラッシュの画像(`CustomFlash`。色付きの粒子で表示します)、曳光弾。
- 薬莢・マガジン・マズルフラッシュの項目がない銃は、Tudur's Guns の既定になります(銃なら、撃つたびに薬莢、リロードでマガジン)。
- **その他:** スクリプト(`addscripts/`、`RendeScript`、`GunScript`)、ノーマルレンダーの動き(`Mat22`〜`Mat32`)、発射レートの切替、クリエイティブタブ、GVC(ゲリラ)連携、素材アイテム(`SimpleMaterial`)。

## 模型の変換

- HMG の模型は +Z が前、Tudur's Guns は -Z が前です。Y 軸まわりに180度回します(X と Z の符号を反転)。
- 大きさは `ModelScala × --scale-factor` 倍です(1 = 1ブロック)。
- 原点は、銃口から模型の長さの 63%(拳銃は 78%)後ろ、高さの 55% の位置です。サンプル銃のグリップ付近に当たります。
- 照準位置(`aim.sight_position`)は、原点付近でいちばん高い点(照門・機関部の上面)を推定しています。
- 部品の回転の中心・移動量・回転も同じ変換をします(回転は X と Z の符号を反転)。
