# Ledger 字体资源

本目录字体文件用于 ledger 报告 HTML / PDF 渲染。

## 文件清单

| 文件名 | 字体 | 字重 | 大小 | 来源 |
|---|---|---|---|---|
| `NotoSerifSC-Regular.otf` | 思源宋体（Noto Serif CJK SC） | Regular (400) | ~24MB | https://github.com/notofonts/noto-cjk |
| `NotoSansSC-Regular.otf` | 思源黑体（Noto Sans CJK SC） | Regular (400) | ~16MB | https://github.com/notofonts/noto-cjk |

## 许可证

Noto CJK 字体使用 **SIL Open Font License 1.1 (OFL)**。
完整许可证文本：https://github.com/notofonts/noto-cjk/blob/main/Serif/LICENSE

OFL 允许：
- 商业与非商业使用
- 修改
- 嵌入到产品分发

OFL 要求：
- 保留版权声明
- 修改后的衍生字体不能使用原字体名

## 引用方式

被 `assets/styles/ledger.css` 的 `@font-face` 通过相对路径 `./fonts/<name>.otf` 引用。

## 大小注意

40MB 字体被 commit 到 git 是常规做法（避免 CDN 依赖与运行时下载失败 — 见 BUG-0049 教训）。
未来若 jar 体积压力大，可考虑：
- 子集化（用 `pyftsubset` 压缩到常用 5000+ 汉字，体积可降至 5MB 以下）
- 字体作为 release artifact 单独分发
- 切换到更轻的开源字体（如方正书宋）
