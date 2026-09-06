# xsaw 開発ロードマップ & README 乖離状況 (TODO.md)

本書は、[README.md](README.md) に記載されている仕様・構想と、現行コードベースの実装状況との**乖離（ギャップ）**を整理し、今後の開発タスクをまとめたチェックリストです。

---

## 1. 全体実装ステータス対照表

| 機能分類 | 機能名 | README記載 | コード実装 | 状態 |
| :--- | :--- | :---: | :---: | :--- |
| **Directory Analysis** | ディレクトリ容量・件数解析 (`du`, `d`) | ◯ | ◯ | **完了** (42 tests PASS) |
| | 拡張子ランキング表示 (`-n`) | ◯ | ◯ | **完了** |
| | 4列グリッド一覧表示 (`-l`) | ◯ | ◯ | **完了** |
| | 拡張子フィルタ (`-e`, 複数指定対応) | ◯ | ◯ | **完了** |
| | 標準入力パイプ集約 (`du -`, `\| du`) | ◯ | ◯ | **完了** |
| **File Search** | 仮想スレッド並行検索 (`fi`, `f`) | ◯ | ◯ | **完了** (42 tests PASS) |
| | オプション (`-s`, `-d`, `-f`, `-e` 複数指定対応) | ◯ | ◯ | **完了** |
| | パイプライン連携 (`f \| du`) | ◯ | ◯ | **完了** (stdout/stderr分離) |
| **Packaging** | Windows / Linux ネイティブ化 (`packageNative`) | ◯ | ◯ | **完了** (`dist/xsaw`) |
| **File Operations** | ファイル移動コマンド (`xsaw mv`) | ◯ | ❌ | 🚨 **未実装** (最優先タスク) |
| **Operation History** | SQLite による操作履歴ロギング | ◯ | ❌ | 🚨 **未実装** (DB基盤が必要) |
| **Conflict Handling** | 移動先の競合検知 & 対話型解決 (1〜5) | ◯ | ❌ | 🚨 **未実装** (UI/CLIプロンプト) |
| **Restore** | 操作ロールバック (`xsaw undo`) | ◯ | ❌ | 🚨 **未実装** (履歴からの逆移動) |
| **Planned Features** | 重複ファイル検知、ハッシュ計算など | ◯ (予定) | ❌ | 🔮 **将来構想** |

---

## 2. 詳細な乖離分析 (Gap Analysis)

### 🚨 乖離 1: ファイル移動コマンド (`xsaw mv`)
- **README の記述**:
  `xsaw mv <source> <destination>` でファイルを移動し、操作を自動記録する。
- **現在のコードベース**:
  - `App.java` に `MvCommand` は未定義。
  - ファイル移動のコアロジック（`Files.move`、ディレクトリまたぎの対応）が未作成。
- **必要なタスク**:
  - [ ] `MvCommand` の picocli アノテーション定義（エイリアス `m` の検討含む）。
  - [ ] 移動元ファイル・移動先パスの存在チェック・バリデーション。
  - [ ] `Files.move` によるアトミック/安全なファイル移動処理。

---

### 🚨 乖離 2: SQLite による操作ログ (`Operation History`)
- **README の記述**:
  全ファイル操作を SQLite に記録。保持情報：`Operation`, `Timestamp`, `Source path`, `Destination path`, `File size`, `Hash`, `Status`。
- **現在のコードベース**:
  - `app/build.gradle.kts` に SQLite JDBC ドライバ（例: `org.xerial:sqlite-jdbc`）の依存関係が**未追加**。
  - DB 接続管理、スキーマ初期化（DDL）、レコード保存のコードが存在しない。
- **必要なタスク**:
  - [ ] `build.gradle.kts` に `sqlite-jdbc` 依存関係を追加。
  - [ ] SQLite データベースファイルの保存場所決定（例: `~/.xsaw/history.db` またはプロジェクトローカル `.xsaw.db`）。
  - [ ] テーブル作成 DDL の定義（`CREATE TABLE IF NOT EXISTS operations (...)`）。
  - [ ] 操作ログのエンティティクラス・DAO（Data Access Object）の実装。

---

### 🚨 乖離 3: 競合検出 & 対話型コンフリクト解決 (`Conflict Handling`)
- **README の記述**:
  移動先に同名ファイルが存在する場合、暗黙の上書きをせず警告を表示し、対話型メニューでアクションを選択させる：
  ```text
  Conflict detected.
  Choose an action:
  [1] Overwrite
  [2] Rename
  [3] Skip
  [4] Compare
  [5] Cancel
  ```
- **現在のコードベース**:
  - 競合検知ロジック、コンソールプロンプト（対話型入力）の仕組みが未作成。
- **必要なタスク**:
  - [ ] 移動先パスの重複チェック（`Files.exists(dest)`）。
  - [ ] コンソール入力ハンドラ（`System.console()` または `Scanner(System.in)`）による対話メニュー表示。
  - [ ] 各アクションのハンドリング：
    - `[1] Overwrite`: 上書き移動。
    - `[2] Rename`: 自動リネーム（例: `foo (1).zip`）またはユーザー入力名への変更。
    - `[3] Skip`: 移動をスキップして終了コード 0。
    - `[4] Compare`: ファイルサイズ・更新日時・ハッシュ値の比較表示。
    - `[5] Cancel`: 操作全体の中断（終了コード 1）。
  - [ ] 非対話環境（パイプや CI）向けの強制フラグ（例: `-y, --yes` または `--force`）の検討。

---

### 🚨 乖離 4: 操作の復元・取り消し (`xsaw undo`)
- **README の記述**:
  `xsaw undo <operation-id>` により、過去の操作を元に戻す。元のパスを事前にチェックして事故を防ぐ。
- **現在のコードベース**:
  - `UndoCommand`（または `xsaw undo`）が未定義。
- **必要なタスク**:
  - [ ] `UndoCommand` の追加。
  - [ ] SQLite から指定 ID（または直近の操作）のレコードを取得。
  - [ ] 移動元（逆操作先）にファイルが既に存在していないかの安全性検証。
  - [ ] 逆方向移動の実行と、取り消し完了ログの記録。

---

## 3. 今後の推奨開発ステップ (Phase Plan)

```
Phase 1: SQLite 基盤 & 基本の mv コマンド
  ├── sqlite-jdbc の導入
  ├── 履歴テーブルの設計・自動マイグレーション
  └── xsaw mv の基本移動 & ログ保存の実装

Phase 2: 競合解決 (Conflict Handling)
  ├── 移動先の重複検知
  └── 対話型プロンプト (Overwrite, Rename, Skip, Compare, Cancel)

Phase 3: 操作取り消し (xsaw undo)
  ├── 履歴参照コマンド (xsaw history / log)
  └── 逆移動による undo コマンドの実装

Phase 4: 機能強化 & 将来構想
  ├── ファイルハッシュ計算 (SHA-256)
  └── 重複ファイル検出 (Duplicate Detection)
```

---

## 4. 進捗チェックリスト

### ディレクトリ解析 & 検索（完了済み）
- [x] `xsaw du` / `xsaw d`: ディレクトリ容量・拡張子統計
- [x] `-n, --topN`: 拡張子上位 N 件表示
- [x] `-l, --list-only`: 4列グリッド拡張子一覧
- [x] `xsaw fi` / `xsaw f`: 仮想スレッド高速検索
- [x] `-s, -d, -f, -e`: 検索オプション完備
- [x] `xsaw f | xsaw du`: パイプライン連携
- [x] `packageNative`: Windows (.exe) & Linux (ELF) ネイティブビルド
- [x] ユニットテスト 38 件全件合格

### ファイル操作 & 履歴管理（未着手）
- [ ] `sqlite-jdbc` の Gradle 依存関係追加
- [ ] 操作ログ用 DB スキーマ（テーブル設計）
- [ ] `xsaw mv` コマンドの実装
- [ ] ファイル移動の自動 SQLite ロギング
- [ ] 対話型コンフリクト解決（Overwrite / Rename / Skip / Compare / Cancel）
- [ ] `xsaw history` コマンド（操作履歴の一覧表示）
- [ ] `xsaw undo <id>` コマンド（ファイル移動の取り消し・復元）
- [ ] 単体テスト（SQLite モック / @TempDir を使った移動・undo の検証）
