# 分支策略规范 — data-talk

本文档定义 data-talk 项目的 Git 分支管理策略，所有贡献者必须严格遵守。

---

## 1. 分支定义

| 分支 | 用途 | 允许操作 | 禁止操作 |
|------|------|----------|----------|
| `master` | 生产分支，代表已发布的稳定版本 | 接受来自 `develop` 的 merge commit；打 tag；触发 CI 发版 | 直接 push；fast-forward merge；squash merge；从此拉功能分支 |
| `develop` | 日常开发主干，所有功能分支的起点和汇聚点 | 接受来自 `feat/*`、`fix/*`、`hotfix/*` 的 PR；更新版本号；日常 push | 直接向 `master` push；未经 lint/test 的合并 |
| `feat/*` | 功能分支，用于开发单个新功能 | 从 `develop` 拉出；本地自由 commit；完成后向 `develop` 提 PR | 直接合入 `master`；长期不合并堆积大量变更 |
| `fix/*` | 修复分支，用于修复非紧急 bug | 从 `develop` 拉出；完成后向 `develop` 提 PR | 直接合入 `master` |
| `hotfix/*` | 紧急修复分支，仅用于生产环境紧急缺陷 | 从 `master` 拉出；修复后同时合回 `master` 和 `develop` | 用于普通功能开发；长期存活 |

---

## 2. 分支流程图

### 日常功能开发流程

```
develop ──────────────────────────────────────────── develop
 │                                                    ▲
 │  git checkout -b feat/my-feature                   │ PR merge (--no-ff)
 ▼                                                    │
feat/my-feature ── commit ── commit ── push ──────────┘
```

### 发版流程（develop → master）

```
develop ── chore: bump version ── push ──┐
                                         │ git merge --no-ff
master ◄─────────────────────────────────┘
   │
   └── CI: 构建三平台安装包 → 打 tag vX.Y.Z → 创建 GitHub Release
```

### 完整分支关系图

```
master ────────────────────────────────────────────────► (生产)
  │  ▲                                         ▲
  │  │ merge --no-ff (发版)                    │ merge --no-ff (hotfix)
  │  │                                         │
  │  develop ──────────────────────────────────┤◄── hotfix/* ──┐
  │   │  ▲          ▲                          │               │
  │   │  │ PR merge │ PR merge                 │               │
  │   ▼  │          │                          │               │
  │  feat/* ──────  fix/* ──────               │               │
  │                                            │               │
  └──────────────── hotfix/* ──────────────────┘               │
                        │ (同步到 develop)                     │
                        └─────────────────────────────────────-┘
```

---

## 3. 严格规则

1. **禁止直接向 `master` push 代码。** `master` 只能通过 `git merge --no-ff` 接受来自 `develop`（发版）或 `hotfix/*`（紧急修复）的变更。

2. **禁止从 `master` 拉功能分支。** 唯一例外是 `hotfix/*` 分支（仅限生产紧急缺陷）。

3. **发版前必须在 `develop` 上更新版本号。** 两个文件必须同步修改：
   - `client/package.json` → `"version": "X.Y.Z"`
   - `client/src-tauri/tauri.conf.json` → `"version": "X.Y.Z"`
   版本号提交后方可 merge 到 `master`。

4. **`master` 上的每个 commit 必须对应一个 GitHub Release。** 禁止在 `master` 上出现无对应 Release 的 merge commit。

5. **merge 到 `master` 必须使用 `--no-ff`（non-fast-forward merge commit）。** 禁止 squash merge 和 fast-forward merge，以保留完整的合并历史和版本追溯性。

6. **PR 合并到 `develop` 前必须通过 lint 和 test：**
   ```bash
   # 前端
   cd client && pnpm lint && pnpm test
   # 后端
   cd server && mvn verify
   ```

7. **功能分支应保持短生命周期。** 建议单个 `feat/*` 分支存活不超过 2 周。

8. **分支命名必须遵循约定。** 使用 `feat/`、`fix/`、`hotfix/` 前缀，后接简短的英文短语（kebab-case）。

---

## 4. 发版操作步骤

```bash
# Step 1: 确保 develop 是最新的
git checkout develop && git pull origin develop

# Step 2: 更新版本号（两个文件必须同步）
# 编辑 client/package.json → "version": "X.Y.Z"
# 编辑 client/src-tauri/tauri.conf.json → "version": "X.Y.Z"

# Step 3: 提交版本号变更
git add client/package.json client/src-tauri/tauri.conf.json
git commit -m "chore: bump version to vX.Y.Z"
git push origin develop

# Step 4: merge develop → master（使用 --no-ff）
git checkout master
git pull origin master
git merge --no-ff develop -m "release: vX.Y.Z"
git push origin master

# CI 自动触发：构建三平台安装包 → 打 tag v{version} → 创建 GitHub Release
```

---

## 4.1 Release Notes 规范

```markdown
## vX.Y.Z Release Notes

✨ **新功能**

- **功能名称**：简短描述，说明用户侧的变化和价值

---

🐛 **Bug 修复**

- **问题描述**：修复了什么问题，用户此前会遇到什么现象

---

🔧 **构建与发布**（可选，仅有 CI/CD 层面变更时填写）

- 简短说明构建、打包、签名、平台兼容性方面的改进
```

**撰写规则：**

1. **以用户视角描述**，避免直接复制 commit message
2. **新功能**（`feat:`）→ 放入 ✨ 新功能
3. **Bug 修复**（`fix:`）→ 放入 🐛 Bug 修复；CI/构建类 fix 归入 🔧 构建与发布
4. **纯内部变更**（`chore:`、`refactor:`、`docs:`）不出现在 Release Notes 中
5. **同一模块多个 fix** 可合并为一条

---

## 5. 版本号规则（SemVer）

版本号格式：`MAJOR.MINOR.PATCH`，遵循 [Semantic Versioning 2.0.0](https://semver.org/)。

| 变更类型 | 操作 | 示例 |
|----------|------|------|
| 重大/破坏性变更（API 不兼容、数据库 schema 重构等） | MAJOR +1，MINOR 和 PATCH 归零 | `0.3.1` → `1.0.0` |
| 新功能（向后兼容，如新增数据源支持、新 UI 模块） | MINOR +1，PATCH 归零 | `0.1.0` → `0.2.0` |
| Bug 修复、性能优化、小改动（向后兼容） | PATCH +1 | `0.1.0` → `0.1.1` |

**版本号唯一真相来源：`client/src-tauri/tauri.conf.json`**，`client/package.json` 必须与之保持同步。
CI 读取 `tauri.conf.json` 中的版本号自动打 tag，两者不一致时 CI 构建失败。

> **注意：** `server/pom.xml` 中的版本号（`0.0.1-SNAPSHOT`）由 Maven 独立管理，不与桌面客户端版本联动。服务端版本仅在涉及 API 兼容性变更时需要同步更新。

---

## 6. 日常开发流程

```bash
# Step 1: 从最新的 develop 拉出功能分支
git checkout develop && git pull origin develop
git checkout -b feat/my-feature

# Step 2: 开发、提交
git add <files>
git commit -m "feat: 描述本次变更"

# Step 3: 保持分支与 develop 同步
git fetch origin
git rebase origin/develop

# Step 4: 推送并创建 PR 到 develop
git push origin feat/my-feature
# 在 GitHub 上创建 PR: feat/my-feature → develop

# Step 5: PR 合并后清理
git branch -d feat/my-feature
git push origin --delete feat/my-feature
```

### PR 描述规范

PR 标题格式：`feat: 简短描述` / `fix: 简短描述`

PR body 应包含：
- 变更目的（Why）
- 主要改动点（What）
- 测试方式（How to test）
- 相关 issue 链接（如有）

---

## 7. 紧急修复流程（hotfix）

```bash
# Step 1: 从 master 拉 hotfix 分支
git checkout master && git pull origin master
git checkout -b hotfix/critical-bug

# Step 2: 修复问题，提交
git add <files>
git commit -m "fix: 描述紧急修复内容"

# Step 3: 更新版本号（patch +1）
# 编辑 client/package.json 和 client/src-tauri/tauri.conf.json
git add client/package.json client/src-tauri/tauri.conf.json
git commit -m "chore: bump version to vX.Y.Z (hotfix)"

# Step 4: 合回 master（触发发版）
git checkout master
git merge --no-ff hotfix/critical-bug -m "hotfix: vX.Y.Z"
git push origin master

# Step 5: 同步到 develop（防止下次发版时修复丢失）
git checkout develop
git merge --no-ff hotfix/critical-bug -m "hotfix: sync to develop"
git push origin develop

# Step 6: 清理 hotfix 分支
git branch -d hotfix/critical-bug
git push origin --delete hotfix/critical-bug
```

---

## 8. CI/CD 集成说明

| 触发条件 | CI 行为 |
|----------|---------|
| push 到 `master` | 读取 `client/src-tauri/tauri.conf.json` 中的版本号，**构建后端 sidecar**（`scripts/bundle-backend.sh`：fat jar + jlink 全模块 JRE 注入 `client/src-tauri/backend/`），构建 Windows/macOS/Linux 三平台安装包（含内嵌后端），打 tag `v{version}`，创建 GitHub Release 并上传安装包 |
| push 到 `develop` | 执行前端 lint/test + 后端 `mvn verify` |
| PR 到 `develop` / `master` | 执行前端 lint/test + 后端 `mvn verify` |

**防重复发版机制：** CI 在打 tag 前会检查 tag 是否已存在。若 `v{version}` tag 已存在，CI 跳过构建。同一版本号只能发版一次，需要重新发版必须先升版本号。

详见 `.github/workflows/release.yml`。

---

## 附录：Commit Message 规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/)：

| 前缀 | 场景 |
|------|------|
| `feat:` | 新功能 |
| `fix:` | Bug 修复 |
| `chore:` | 构建、依赖、版本号等维护性变更 |
| `refactor:` | 重构（不改变功能） |
| `docs:` | 文档变更 |
| `test:` | 测试相关 |
| `ci:` | CI/CD 配置变更 |
| `perf:` | 性能优化 |
| `release:` | 发版 merge commit（仅用于 master） |
| `hotfix:` | 紧急修复 merge commit |
