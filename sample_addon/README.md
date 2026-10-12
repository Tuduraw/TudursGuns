# Tudur's Guns サンプルアドオンパック

Tudur's Guns に武器・防具などを追加するアドオンパックの見本です。
モデルは [meshkit](https://github.com/Tuduraw/Meshkit) を使い、Python のスクリプトだけで作っています(Blender は使っていません)。

- `tgsample_pack/` … アドオンパック本体(名前空間 `tgsample`)
- `tools/build_pack.py` … パックの中身をすべて作るスクリプト

## 導入

1. Tudur's Vehicle Mod と Tudur's Guns を入れます。
2. `tgsample_pack` フォルダを、圧縮せずにそのまま `<ゲームフォルダ>/tudursvehiclemod-addons/` に置きます。
3. ゲームを起動します。アイテムは Tudur's Guns のクリエイティブタブに並び、銃器製作台でも作れます。

音は Tudur's Guns 本体の音を名前で使っています(`tg_revolver_shot` など)。パックには音声ファイルは入っていません。

## 収録内容

| 種類 | ID(`tgsample:`) | 内容 |
|---|---|---|
| 携帯武器 | `compact_pistol` | 拳銃。15発、セミオート。スライドが動き、リロードで弾倉を抜き差しします |
| | `smg` | 短機関銃。30発、フルオート。アタッチメント枠3つ(照準器・銃口・グリップ) |
| | `pump_shotgun` | ポンプ式散弾銃。6発、1発で8粒。撃つたびにフォアエンドを引きます |
| アタッチメント | `red_dot` | ドットサイト。付けると照準点がサイトに移ります(3丁すべてに付きます) |
| | `compact_suppressor` | サプレッサー。音が小さくなり、マズルフラッシュが消えます(拳銃・短機関銃) |
| | `vertical_foregrip` | フォアグリップ。反動とばらつきが減ります(短機関銃) |
| 弾薬 | `pistol_magazine` / `smg_magazine` | 拳銃・短機関銃の弾倉(15発 / 30発) |
| | `shotgun_shells` | 散弾銃の弾(1個1発) |
| 投擲物 | `impact_grenade` | 当たった所で爆発する手榴弾 |
| | `stick_grenade` | 柄付き手榴弾。遠くまで投げられ、投げてから約4.5秒で爆発します |
| | `colored_smoke` | 赤い煙幕(目印用) |
| 地雷 | `bounding_mine` | 近づくと少し遅れて爆発します |
| | `demolition_charge` | 遠隔爆薬。Tudur's Guns の起爆装置で起爆します。どの面にも車両にも付きます |
| 関連装備 | `field_medkit` | 応急手当キット(3回使用) |
| | `compact_binoculars` | 小型の双眼鏡(2〜4倍、距離表示付き) |
| 防具 | `boonie_hat` | ブーニーハット(頭)。見つかりにくくなります |
| | `assault_vest` | アサルトベスト(胴)。弾と爆風を少し防ぎます |
| | `knee_pads` | ニーパッド(脚)。左右の脚に別々に付き、脚と一緒に動きます |

武器と防具には `soldier_weight` を設定しているので、敵兵士もこれらを装備して出てきます。

## パックの構成

```
tgsample_pack/
  assets/tgsample/
    weapons/tgs_*.txt          武器ファイル(前提MODと同じ書式。名前は全MOD共通なので接頭辞 tgs_ を付けています)
    models/obj/*.obj           モデル
    textures/vehicle/*.png      モデルのテクスチャと、弾薬のインベントリ用の画像
  data/tgsample/
    handheld/ attachment/ ammo/ throwable/ mine/ equipment/ armor/   定義(キーは Tudur's Guns の README を参照)
    gun_recipe/                銃器製作台のレシピ
```

## 作り直し・改造

```
pip install numpy Pillow
python sample_addon/tools/build_pack.py --preview preview_out
```

- meshkit は、環境変数 `MESHKIT_PATH`、またはこのリポジトリと同じ階層の `tuduraw/meshkit` か `meshkit` から読み込みます。
- 出力を手で直すのではなく、スクリプトの数値を直して実行し直してください。モデル・テクスチャ・アイコン・定義・武器ファイル・レシピがすべて作り直されます。
- `--preview` を付けると、モデルごとの確認用の図(斜め・横・上から)を書き出します。
- スクリプトはモデルを書き出す前に meshkit の検査(穴・巻き順・裏返り・浮き部品)を行い、問題があれば止まります。

### 座標の決まり(1 = 1ブロック)

- 武器とアタッチメント:銃身が -Z、上が +Y、原点がグリップ(Tudur's Guns の決まり)。
- 防具:着ける部位の空間で作ります。原点は部位の付け根(頭と胴は首、腕は肩、脚は股関節)、+Y が上、+Z が前、-X が着ている人の右、スキンの1ピクセル = 1/16。グループ名を `head`・`body`・`right_leg` などにすると、その部位に付きます(ニーパッドは `right_leg` と `left_leg`)。

## 注意

- コンパイル済みの Tudur's Guns で、定義がすべて読み込めることは確かめています。ゲーム内での見た目は確かめていません。
- テクスチャはすべて `textures/vehicle/` に置いています。アドオンフォルダはリソースパックではなく、前提MODはこのフォルダの下のテクスチャしか読み込まないためです(Tudur's Guns は `textures/` の下ならどこでも読み込みます)。同じ理由で、防具のインベントリ表示にはバニラのアイテムモデルを使わず、OBJ モデルを `display` で縮小して表示しています。
- 手に持ったときの位置(`display`)と一人称の構え(`aim`)は、Tudur's Guns 本体のサンプル(リボルバー・サンプルライフル・歩兵銃など)の値を、モデルの大きさに合わせて流用しています。ずれている場合は `build_pack.py` の値を直してください。

## クレジット

- 前提MOD:Tudur's Vehicle Mod(作者 Tuduraw 氏)
- モデル作成:meshkit(作者 Tuduraw 氏)
- このパックの内容(モデル・テクスチャ・定義・スクリプト)はすべて自作です。他者が権利を持つデータは使っていません。
- 制作には Claude(Anthropic)を使用しました。
