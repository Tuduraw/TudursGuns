# 前提MOD(Tudur's Vehicle Mod)への変更案

変更の目的と内容は `docs/DESIGN_DECISION.md` の「前提MODへの変更」を参照。

- 基準コミット: `82819f9`(Tuduraw/tudursvehiclemod の main)
- **反映済み**: 前提MODの `337153f` として取り込まれた(内容は `files/` と同一)。このフォルダは記録として残している

## 中身

| パス | 内容 |
|---|---|
| `files/` | 変更後のファイル。前提MODのリポジトリと同じ階層構造 |
| `0001-share-weapon-firing-with-non-vehicle-shooters.patch` | 同じ変更の `git am` 用パッチ |

`files/` の内容と、パッチを基準コミットに適用した結果は同一。

## `files/` のファイル

| ファイル | 種別 |
|---|---|
| `src/main/java/com/example/tudursvehiclemod/entity/WeaponTargeting.java` | 新規 |
| `src/main/java/com/example/tudursvehiclemod/entity/projectile/WeaponProjectileFactory.java` | 新規 |
| `src/main/java/com/example/tudursvehiclemod/entity/AbstractVehicleEntity.java` | 変更 |
| `src/main/java/com/example/tudursvehiclemod/entity/projectile/VehicleProjectileEntity.java` | 変更 |
| `src/client/java/com/example/tudursvehiclemod/client/VehicleModClient.java` | 変更 |
| `src/client/java/com/example/tudursvehiclemod/client/render/VehicleEntityRenderer.java` | 変更 |
| `Readme_Addon_Mod.md` | 変更 |
| `Another language/en_US/Readme_Addon_Mod.md` | 変更 |

## ローカルでの確認手順

1. 前提MODの作業コピーが `82819f9` と同じ状態であることを確認する。
   - 前提MOD側にこれより新しい変更がある場合、`files/` で上書きするとその変更が消える。
   - その場合はパッチを使う(`git am`)。
2. `files/` の中身を前提MODのリポジトリのルートにコピーする(上書き)。
3. `./gradlew build` を実行する。
4. ゲーム内で動作を確認する。
   - 車両の各武器種の発射
   - AA/AT のロックオン
   - TVミサイルの操縦と、降車したときに操縦が終わること
