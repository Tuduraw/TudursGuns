# Mojang 公式マッピングへの移行

このMODは、Minecraft 1.21.11 Fabric 版を **Mojang 公式マッピング**(`loom.officialMojangMappings()`)で開発しています。
前提MOD([tudursvehiclemod-mojmap](https://github.com/Tuduraw/tudursvehiclemod-mojmap)、`MOJMAP.md`)に合わせて、Yarn から移行しました。

## 移行した理由

- Yarn の提供は Minecraft 1.21.11 が最後で、以降は Mojang 公式マッピングが推奨されています。
- Forge 1.20.1 版は、前提MODと同じく Mojang 名のソースから自動生成する方式で作ります。前提MODの変換ツールと互換層は、
  1.20.1 に無い API を 1.21.11 の Mojang 名(`StreamCodec`・`ValueInput` など)で受け取るため、ソースが Mojang 名である必要があります。

Fabric 1.21.11 版だけなら、移行しなくても動作します。jar は前提MODを中間名(intermediary)で参照するため、
前提MODとこのMODのマッピングが違っていても問題ありません。

## 移行の方法

前提MODの `tools/mojmap/` と同じ手順で行いました。

1. Yarn 版のプロジェクトのコピーで、Loom の `migrateClientMappings` を main と client のソースを合わせて実行
   (`--mappings net.minecraft:mappings:1.21.11`)
2. `build.gradle` のマッピングを `loom.officialMojangMappings()` に、`gradle.properties` から `yarn_mappings` を削除
3. 変換後のソースをコンパイルし、残った Yarn 名を前提MODの `fix_leftovers.py` で直す
4. 残りを手で直す。総称型の型引数で宣言したオーバーライドは変換されないため、次の名前を変更:

| クラス | Yarn 名 | Mojang 名 |
|---|---|---|
| `ObjHandheldModelRenderer`・`ObjDefinedItemRenderer` | `getData`・`render` | `extractArgument`・`submit` |
| `ObjArmorFeatureRenderer` | `render` | `submit` |
| `MineEntityRenderer` | `updateRenderState`・`render` | `extractRenderState`・`submit` |
| `SoldierRenderer` | `getTexture` | `getTextureLocation` |

Mixin の対象(`@Inject(method = "...")`・`@Accessor`・`@Invoker` の文字列)は `migrateClientMappings` が変換しました。
すべて Mojang 名の Minecraft jar に存在することを確認しています。

## Yarn 版との同等性

Yarn 版と Mojang 版の jar を、前提MODの `tools/mojmap/compare_jars.py` で中間名の段階でメンバー単位に比べました。

- クラス(190個)・リソースの構成は完全に一致
- Mixin の注釈の対象(中間名に変換されたもの)は 17 クラスすべてで一致
- 違いは次だけで、どれも実行時の動作には関係ありません
  - 上の表の、総称型のオーバーライドの名前(Minecraft からはブリッジメソッド経由で呼ばれ、ブリッジの呼び先が変わっただけ)
  - `switch` 文用に javac が作る合成フィールドの名前(列挙型のクラス名を含むため。`$SwitchMap$net$minecraft$core$Direction` など)

比べ直すとき:

```
python <tudursvehiclemod-mojmap>/tools/mojmap/compare_jars.py <Yarn版のjar> <今のjar> <作業フォルダ> \
    --yarn ~/.gradle/caches/fabric-loom/1.21.11/net.fabricmc.yarn.*/mappings.tiny \
    --mojmap ~/.gradle/caches/fabric-loom/1.21.11/loom.mappings.*layered*/mappings.tiny
```

## 残っている Yarn の名残

コメント・Javadoc 内のメソッド名や、引数・ローカル変数の名前(`world` など)は変換されていません。
動作には関係ないので、編集のついでに Mojang 名へ直していってください。
