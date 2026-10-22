# Blender 场景与运行基线

本目录是 2026-10-22 从独立模型项目选取的可继续开发快照，不包含全部历史迭代。

| 文件 | 用途 |
| --- | --- |
| [zen-space.blend](zen-space.blend) | 当前 p3-v053 可编辑主模型；技术 PASS、视觉 PARTIAL |
| [zen-space-p3-v039.blend](zen-space-p3-v039.blend) | 移动运行资源对应的可编辑基线 |
| [v039 GLB](exports/zen-space-p3-v039.glb) | 已固定移动分包资源的导出来源 |
| [v053 GLB](exports/zen-space-p3-v053.glb) | 最新全园导出；尚未替换应用的 v039 运行基线 |

大文件通过 Git LFS 保存。Blender 5.2.2 LTS，米制、+X 东、+Y 北、+Z 上；主模型图片和字体已打包，不依赖本机链接库。GLB 不能替代 Blender 灯光、节点、状态和业务实现。

## 清单与许可

- [场景清单](manifest/scene-manifest.json)
- [相机登记](manifest/camera-register.csv)
- [资产登记](manifest/asset-register.csv)
- [第三方许可](manifest/LICENSES.md)
- [来源当前版本记录](evidence/current-work-manifest.json)

`previews/runtime/vendor/` 保存应用场景构建所需的公共依赖。`scripts/` 保留原有制作/导出脚本，部分历史脚本依赖未导入的旧版本和工作站素材；不要将它们直接应用到主模型或清空未知对象。需要修改时另存下一版，不覆盖当前来源文件。

本轮 v053 的完整原始渲染图册及历史短片未上传。历史报告中的旧图像路径仅用于追溯，不能据此宣称本仓库已完成视觉或设备验收。
