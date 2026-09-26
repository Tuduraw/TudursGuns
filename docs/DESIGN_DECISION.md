# 設計判断: アドオンMODか、互換性のある独立MODか

対象: Tudur's Vehicle Mod(以下「前提MOD」、`tudursvehiclemod` 1.0.3 / Fabric 1.21.11)と連携する、銃などの携帯装備MOD(以下「本MOD」)。

## 結論

**前提MODを必須依存とするアドオンMODとして作成する。**

ただし、前提MODの内部APIへの依存は本MOD内の `compat`(アダプタ)層に集め、前提MOD側には後述の小さなAPI追加を行う。前提MODは同じ作者の管理下にあるため、上流を直せることを前提にしている。

### 決め手

1. **独立MODでは弾が車両に当たらない。**
   - 前提MODの `ProjectileVehicleHitMixin` は、すべての `ProjectileEntity` について車両への `canHit` を `false` に固定している。
   - 代わりに使われる独自のメッシュ命中判定(`AbstractVehicleEntity#tudursvehiclemod$updateCustomHitDetection`)は、`VehicleProjectileEntity` がワールドに1つも無いと何もしない(`anyActiveAnywhere()` による早期リターン)。
   - 判定が動いた場合でも、他MODの弾は以下の扱いになる。
     - ダメージは `clamp(速度×6, 1, 10)` の推定値になる。
     - 着弾処理(爆発など)は実行されず、`discard()` されるだけ。
   - 独立MODにしても、結局この部分は上流の修正が必要になる。「全部自前で作るのに、上流修正も要る」という一番悪い形になる。
2. **`VehicleProjectileEntity` の弾道挙動はプレイヤーを発射者にしても動く。**
   - 公開コンストラクタは発射者として任意の `LivingEntity` を受け取る。
   - 以下はすべて車両なしで動作する。
     - 誘導(エンティティ追尾・地点追尾・トップアタック・近接信管)
     - 各種信管、子弾、貫通、跳弾、水中挙動
     - 車両メッシュへの正確な命中判定と爆風ダメージ
   - フレアの欺瞞対象にもなる。
   - 携帯ミサイル(MANPADS/ATGM)やロケットランチャーを作るうえで最も価値のある部分。
3. **データ形式と資産を共有できる。**
   - 武器 `.txt`(MCHeli形式)を jar の `assets/<ns>/weapons/` に置くだけで `WeaponStatsLoader` に読み込まれる。
   - 以下の仕組みもそのまま使える。
     - `/reload`
     - `tudursvehiclemod-addons/` フォルダでの上書き
     - OBJ ローダー
     - 半透明(ディザ)描画レイヤー
     - HUD スクリプトエンジン
     - OGG の自動読み込み
   - 同じ武器定義を車載武装と携帯装備の両方で使える。

### 独立MODを選ばなかった理由

| 観点 | アドオン | 独立MOD(任意連携) |
|---|---|---|
| 弾体の対車両命中 | `VehicleProjectileEntity` を使えば正確 | 上流修正なしでは素通りまたは不正確 |
| 誘導ミサイル・フレア | そのまま流用できる | 自前実装。前提MODのフレアは効かない |
| 武器定義・OBJ・HUD・音 | 流用できる | 自前実装か、リフレクションによる任意連携 |
| 前提MODなしで遊べるか | 遊べない | 遊べる |
| 保守リスク | 前提MODの内部API変更の影響を受ける | 影響は小さい |

「前提MODなしで遊べる」ことは利点だが、この企画は前提MODとの連携を前提にしている。保守リスクは `compat` 層で局所化できる。

---

## 引き継げる要素

### そのまま使える(公開API)

| 分類 | 要素 | 場所(前提MOD) |
|---|---|---|
| 武器データ | `WeaponStatsLoader.get(name)`, `WeaponStats`(約95項目、`has*()` 補助) | `asset/WeaponStatsLoader.java`, `asset/WeaponStats.java` |
| 武器データ | jar 内 `assets/<ns>/weapons/*.txt` の自動読み込み、`/reload`、アドオンフォルダでの上書き | `WeaponStatsLoader#reload` |
| 弾体 | `VehicleProjectileEntity` / `VehicleModelProjectileEntity` の公開コンストラクタ(発射者は `LivingEntity`) | `entity/projectile/` |
| 弾体 | `tudursvehiclemod$set*` 系の設定メソッド(誘導目標、信管、子弾、貫通、跳弾、水中、軌跡パーティクルなど) | 同上 |
| 描画 | `ObjModelLoader.get(id)`(自MOD jar 内の `models/obj/*.obj` も自動で読み込まれる)、`ObjModel` のグループ取得 | `client/render/` |
| 描画 | `DitherCutoutLayers.entityDitherCutout(texture)`(ユーザーの半透明設定に従う) | `client/render/DitherCutoutLayers.java` |
| HUD | `HudScript` / `HudExpr` / `HudExecutionContext` / `HudScriptLoader.getScripts()`(変数マップは本MOD側で用意する) | `client/hud/` |
| HUD | `MortarMarkerRenderer.tudursvehiclemod$computeCollisionDistance(...)`(弾道の着弾点計算) | `client/hud/MortarMarkerRenderer.java` |
| 音 | `WeaponFireSoundPayload`(サーバーから送信)、`WeaponFireSoundManager.play`、OGG の自動読み込み | `network/`, `client/sound/` |
| 車両連携 | `getEffectiveVehicle(player)`(搭乗判定)、`damage()`, `isPointNearMeshSurface(Vec3d)`(ヒットスキャン用) | `entity/AbstractVehicleEntity.java` |
| 装備 | `ParachuteItem` / `ParachuteEntity.tudursvehiclemod$spawnAndMount`(個人用パラシュートは前提MODのものを使う) | `item/`, `entity/` |

### 前提MODへの変更

前提MOD側の変更はパッチとして `upstream/tudursvehiclemod/0001-share-weapon-firing-with-non-vehicle-shooters.patch` に置いている。前提MOD `82819f9` に `git am` で適用できる。

#### 実施した変更

| 変更 | 内容 | 既存の動作への影響 |
|---|---|---|
| `WeaponProjectileFactory.create(world, owner, stack, WeaponStats, mode)` を追加 | `tryFireWeapon` の中で武器ファイルから弾体を設定していた部分を切り出した。車両側もこれを呼ぶ | 設定する項目・順序・`ModeNum` の扱いは同じ。`WeaponDefinition` の各アクセサは `WeaponStatsLoader.get(weaponName)` への委譲なので、`WeaponStats` から直接読んでも値は同じ |
| `WeaponTargeting` を追加 | ロックオン探索、目標の空中/地上/水中判定、対地照準点、`Accuracy` のばらつきを static メソッドにした | 車両側の private/protected メソッドは残し、中身を委譲にした。サブクラスが `classifyTargetPosition` を上書きしている場合も、その上書きが使われる |
| TVミサイルの操縦終了条件 | 車両なしで発射された場合(`firingVehicle == null`)の条件を追加した。操縦者の死亡・ログアウト・ディメンション移動、または何かへの搭乗で終了する | 車両から発射した TVミサイルは必ず `firingVehicle` を持つので、従来の分岐のまま |
| TVミサイルの操縦入力(クライアント) | 入力送信処理を「搭乗中」の分岐の外へ移した | 搭乗中は同じ tick・同じ順序で実行される。非搭乗中は、車両から撃ったミサイルは操縦が即座に終了するため送信は起きない |
| `VehicleEntityRenderer.renderTriangles(..., light, overlay, tint)` を追加 | overlay を受け取る public なオーバーロード | 既存の package-private メソッドは残し、`OverlayTexture.DEFAULT_UV` を渡して新メソッドを呼ぶだけにした。描画結果は同じ |
| `Readme_Addon_Mod.md`(日本語・英語) | 「8. 車両以外から武器を発射する」を追加し、「制限事項」を 9 に繰り下げた | — |

#### 互換性の確認

- **既存のメソッド**
  - 削除やシグネチャの変更はしていない。
  - 削除したのは、参照がなくなった `AbstractVehicleEntity` の private 定数 4 つだけ。これらは `WeaponTargeting` の public 定数に移した。
- **設定ファイル・データ**
  - 車両 JSON、武器 txt、Config、セーブデータ(NBT)、通信(ペイロード)の形式は変更していない。
- **既存アドオン**
  - `motorcycleaddon`、`humanoidrobotaddon`、`sample_pack_for_tudurs_vehicle` を確認した。
  - 変更したメソッドの呼び出し、上書き、それらを対象にした Mixin はない。
  - `humanoidrobotaddon` の `Camera` / `changeLookDirection` への Mixin は、変更箇所と重ならない。

#### 実施しなかった変更と理由

| 項目 | 理由 |
|---|---|
| 自車被弾の除外 | 搭乗中は携帯装備を使えない仕様にしたため不要 |
| `computeBallisticTargetPoint` の公開 | CAS/Carrier 専用の着弾点計算で、携帯装備では使わない。迫撃砲の着弾表示にはクライアントの `MortarMarkerRenderer.tudursvehiclemod$computeCollisionDistance` が既に公開されている |
| クライアント側の `WeaponStats` | 統合サーバー(シングルプレイ)ではクライアントとサーバーが同じ static マップを共有しているため、クライアントで再読み込みすると競合する。本MODが必要な値だけを自前のパケットで同期する |
| `CustomWeaponBehavior` の汎用化 | 携帯装備で `Type = ns:id` のカスタム武器を使う予定が現時点でない |
| 外部の弾向けのフレア対応 | 本MODは `VehicleProjectileEntity` を使うので、既存のフレア処理がそのまま効く |
| `AddonTextureLoader` の読み込み対象の拡大 | 本MODの jar に入れたテクスチャは通常どおり読み込まれる。アドオンフォルダに置く場合も `textures/vehicle/` に置けば使える |

#### 未検証の点

この環境では Fabric の Maven に接続できないため、前提MODをビルドしていない。確認できているのは次の点だけ。

- 変更したファイルに構文エラーがないこと(`javac` の構文解析)
- パッチが `82819f9` にそのまま適用できること(`git apply --check`)

適用後に前提MOD側で `./gradlew build` と、次の動作確認を行う必要がある。

- 車両の各武器種の発射
- AA/AT のロックオン
- TVミサイルの操縦と、降車したときの操縦終了

### 流用できず、本MODで実装するもの

表示と武装以外は、当初の想定どおり自前で実装する。

- **アイテムと描画**
  - 携帯装備アイテムと、OBJ を描画する `SpecialModelRenderer`(`items/*.json` で `minecraft:special` を使う)。前提MODにはアイテムを OBJ で描画する仕組みが無い。
  - 一人称・三人称・GUI・地面それぞれの表示変換。
- **動作表現**
  - 一人称の腕やビューモデルの姿勢。
  - ADS(FOV変更)、カメラ反動、感度補正。
  - 部位アニメーション(ボルト・マガジン・反動)。前提MODの pivot/回転/平行移動のやり方は参考にして、実装は自前で書く。
- **武器の状態**
  - 装弾数、熱、リロード、クールダウン、射撃モードを `ItemStack` のデータコンポーネントで持つ。前提MODではこれらが車両ごと・武器番号ごとの配列になっている。
- **入力と通信**
  - 射撃・リロード・モード切替の C2S パケットとサーバー側の射撃処理。前提MODの `FireWeaponPayload` は車両専用。
- **ロックオン**
  - ロック進行と対象ハイライトの同期。前提MODでは車両の DataTracker 経由になっている。
- **その他**
  - 弾薬アイテムと、その補給・クラフト。
  - HUD 変数の生成と HUD 要素の登録。
  - 本MOD独自のキーバインド。

---

## 実装時に避けるべき衝突

| 衝突 | 内容 | 対策 |
|---|---|---|
| 右クリック | 前提MODの射撃キーの既定値が右クリック。また `UseItemCallback` が、座席の近く(水平1ブロック以内)での右クリックを乗車として消費する | **搭乗中は携帯装備を使えない仕様とする(決定)**。座席付近の乗車動作はしゃがみで回避できることを案内する |
| 既定キー | 前提MODが R / 左Alt / 左Ctrl / Space / N / B などを使っている | リロードなどの既定キーは重ならないよう選ぶ(R を避ける)。キー設定で変更できる前提にする |
| 搭乗中の手持ち描画 | 前提MODの `HeldItemRendererMixin` が、搭乗中は一人称の手持ち描画を消す | 搭乗中は使えない仕様なので問題にならない |
| 武器名の衝突 | 武器 `.txt` は名前空間なしのファイル名が全体で一意なキーになる | 本MODの武器ファイルには `tg_` などの接頭辞を付ける |
| 定義ファイルの配置 | — | 携帯装備固有の定義(表示・ADS・弾薬アイテムなど)は `data/<ns>/handheld/*.json` に置く。そこから `weapon_name` で武器 `.txt` を参照する。車両の「JSON + 武器txt」の分け方に合わせる |
| パラシュート | 前提MODは Space と胸スロットで展開する | 本MODでは独自のパラシュートを作らない |

## 依存に伴うリスクと対策

| リスク | 対策 |
|---|---|
| 配布: 前提MODの成果物が Maven Local にしか公開されていない(group は `com.example.tudursvehiclemod`) | 開発時は `publishToMavenLocal` を使う。将来的には前提MOD側で公開 Maven か CurseMaven を用意する |
| バージョン固定: Minecraft 1.21.11 と Yarn `1.21.11+build.4` を完全に揃える必要がある | 本MODも同じ値に固定する。`fabric.mod.json` で `tudursvehiclemod` のバージョン範囲を明記する |
| 内部APIの変更: `tudursvehiclemod$` 付きのメソッドは将来シグネチャが変わりうると明記されている | 前提MODへの参照は本MOD内の `compat` パッケージに集める。上流の変更はそこだけで吸収する |
| 次のMinecraftバージョンでのマッピング移行: Yarn の提供は 1.21.11 が最後 | 前提MODと同時に移行する |
