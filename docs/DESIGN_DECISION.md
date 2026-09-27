# 設計判断: アドオンMODか、互換性のある独立MODか

対象: Tudur's Vehicle Mod(以下「前提MOD」、`tudursvehiclemod` 1.0.3 / Fabric 1.21.11)と連携する、銃などの携帯装備MOD(以下「本MOD」)。

## 結論

**前提MODを必須依存とするアドオンMODとして作成する。**

前提MODは同じ作者の管理下にあるため、必要な API は上流に追加する(車両以外からの発射に使う `WeaponProjectileFactory` と `WeaponTargeting`。前提MOD `337153f` で取り込み済み)。

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

「前提MODなしで遊べる」ことは利点だが、この企画は前提MODとの連携を前提にしている。

## 依存に伴うリスクと対策

| リスク | 対策 |
|---|---|
| 配布: 前提MODの成果物が Maven Local にしか公開されていない(group は `com.example.tudursvehiclemod`) | 開発時は `publishToMavenLocal` を使う。将来的には前提MOD側で公開 Maven か CurseMaven を用意する |
| バージョン固定: Minecraft 1.21.11 と Yarn `1.21.11+build.4` を完全に揃える必要がある | 本MODも同じ値に固定する。`fabric.mod.json` で `tudursvehiclemod` のバージョン範囲を明記する |
| 内部APIの変更: `tudursvehiclemod$` 付きのメソッドや private メソッド(`@Invoker` で呼ぶ補給処理)は将来変わりうる | 前提MODと同時に更新する |
| 次のMinecraftバージョンでのマッピング移行: Yarn の提供は 1.21.11 が最後 | 前提MODと同時に移行する |
