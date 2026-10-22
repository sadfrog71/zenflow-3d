# 第三方素材与许可

场景建筑、路径、程序化造景和交互承载面为本次制作；以下外部素材依据记录复用。概念参考为项目提供的设计资料，不作为可自由转售的第三方素材授权。

- Poly Haven HDRI、树、蕨类和木石／地面贴图：CC0。来源及下载校验值见下列原始清单。
- Noto Sans CJK SC：SIL OFL 1.1；字体已打包，授权全文随附 `../assets/fonts/OFL.txt`。
- Three.js r169：MIT；授权全文随运行预览 vendor 文件提供。

## bark-material-source.json

```json
[
  {
    "file": "assets/polyhaven/bark_brown_01_nor_gl_1k.jpg",
    "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/bark_brown_01/bark_brown_01_nor_gl_1k.jpg",
    "license": "CC0",
    "asset": "bark_brown_01"
  },
  {
    "file": "assets/polyhaven/bark_brown_01_rough_1k.jpg",
    "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/bark_brown_01/bark_brown_01_rough_1k.jpg",
    "license": "CC0",
    "asset": "bark_brown_01"
  },
  {
    "file": "assets/polyhaven/bark_brown_01_diff_reused.jpg",
    "source_local": "/Users/mark.ma/Documents/AI coding/zenflow/docs/prototypes/reading-room/assets/bark_brown_01-color.jpg",
    "source": "https://polyhaven.com/a/bark_brown_01",
    "license": "CC0",
    "sha256": "4213441aebcb72b9911f5a860e9ce74a61a8f6ce3b9c23d1f204173e7e7f6066"
  }
]
```

## far-forest-impostor-source.json

```json
{
  "source": "https://polyhaven.com/a/tree_small_02",
  "license": "CC0",
  "author": "Rico Cilliers",
  "derivation": "Cycles transparent orthographic render of source tree trunk/branches/leaf cards, enlarged whole-leaf coverage 1.25x; no reference-image planes",
  "use": "far context only, outside functional garden",
  "sprite_world_aspect": 1,
  "source_tree_height": 4.582284450531006,
  "files": [
    {
      "file": "assets/derived/forest-impostors/tree-1.png",
      "angle_rad": 0.55
    },
    {
      "file": "assets/derived/forest-impostors/tree-2.png",
      "angle_rad": 2.1
    },
    {
      "file": "assets/derived/forest-impostors/tree-3.png",
      "angle_rad": 4.3
    }
  ]
}
```

## font-source.json

```json
{
  "font": "Noto Sans CJK SC",
  "license": "SIL Open Font License 1.1",
  "license_url": "https://github.com/notofonts/noto-cjk/blob/main/Sans/LICENSE",
  "files": [
    {
      "file": "assets/fonts/NotoSansCJKsc-Regular.otf",
      "url": "https://raw.githubusercontent.com/notofonts/noto-cjk/main/Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf",
      "sha256": "2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b",
      "bytes": 16437364
    },
    {
      "file": "assets/fonts/OFL.txt",
      "url": "https://raw.githubusercontent.com/notofonts/noto-cjk/main/Sans/LICENSE",
      "sha256": "6a73f9541c2de74158c0e7cf6b0a58ef774f5a780bf191f2d7ec9cc53efe2bf2",
      "bytes": 4301
    }
  ]
}
```

## forest-ground-files-metadata.json

```json
{
  "Diffuse": {
    "8k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/8k/forest_ground_04/forest_ground_04_diff_8k.jpg",
        "size": 51978989,
        "md5": "dc01af35ee412f428e34371f00f7f640"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/8k/forest_ground_04/forest_ground_04_diff_8k.png",
        "size": 120102924,
        "md5": "8a55f5a0cc0c99f5cacc6e3b878f8ac3"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/8k/forest_ground_04/forest_ground_04_diff_8k.exr",
        "size": 92598367,
        "md5": "f2796c124098d0387cf005534e0b4037"
      }
    },
    "4k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/4k/forest_ground_04/forest_ground_04_diff_4k.jpg",
        "size": 16611792,
        "md5": "68d76337ff8aebe1853dd20783b26008"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/4k/forest_ground_04/forest_ground_04_diff_4k.png",
        "size": 34352704,
        "md5": "8ef79e766dcaed16fafdf6676ede9a5a"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/4k/forest_ground_04/forest_ground_04_diff_4k.exr",
        "size": 29837557,
        "md5": "671ed4a49a60a3269511fbbb1478da37"
      }
    },
    "2k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/forest_ground_04/forest_ground_04_diff_2k.jpg",
        "size": 4529321,
        "md5": "f6ce9c8af4316c2627a4b3fbab8ad944"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/2k/forest_ground_04/forest_ground_04_diff_2k.png",
        "size": 9338911,
        "md5": "93972c83249ae015111c8da714e6162c"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/2k/forest_ground_04/forest_ground_04_diff_2k.exr",
        "size": 8364714,
        "md5": "60d965654ffd38a8edfc1e32191bfdc4"
      }
    },
    "1k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/forest_ground_04/forest_ground_04_diff_1k.jpg",
        "size": 1113899,
        "md5": "6ad9df4d731a238299806f739a26af83"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/1k/forest_ground_04/forest_ground_04_diff_1k.png",
        "size": 2364487,
        "md5": "8f4318ba5044aa147999b08a10302fb4"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/1k/forest_ground_04/forest_ground_04_diff_1k.exr",
        "size": 2147321,
        "md5": "c269ac20049a8245296174a081c2d45c"
      }
    }
  },
  "nor_dx": {
    "8k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/8k/forest_ground_04/forest_ground_04_nor_dx_8k.jpg",
        "size": 64549192,
        "md5": "9bd48d3b43d379986e296d905a7259b1"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/8k/forest_ground_04/forest_ground_04_nor_dx_8k.png",
        "size": 126192021,
        "md5": "5979286aad6d0f21c37b88cf004356a5"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/8k/forest_ground_04/forest_ground_04_nor_dx_8k.exr",
        "size": 123694885,
        "md5": "55d5fafdff627d256fda7fa46239fcf3"
      }
    },
    "4k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/4k/forest_ground_04/forest_ground_04_nor_dx_4k.jpg",
        "size": 19393455,
        "md5": "ad5d5a1e41c367a45ce63298654069b6"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/4k/forest_ground_04/forest_ground_04_nor_dx_4k.png",
        "size": 37822119,
        "md5": "f8357670ddfe69df002444058c817140"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/4k/forest_ground_04/forest_ground_04_nor_dx_4k.exr",
        "size": 37713332,
        "md5": "7105e402c98c476e9dfabbce39a0bdb5"
      }
    },
    "2k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/forest_ground_04/forest_ground_04_nor_dx_2k.jpg",
        "size": 5277194,
        "md5": "68719db3282217192dda41342258e035"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/2k/forest_ground_04/forest_ground_04_nor_dx_2k.png",
        "size": 9986137,
        "md5": "d269f8896df0410506721605931f6416"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/2k/forest_ground_04/forest_ground_04_nor_dx_2k.exr",
        "size": 10170731,
        "md5": "d6d5cfbe1362ae22caa8fd3897d04cd5"
      }
    },
    "1k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/forest_ground_04/forest_ground_04_nor_dx_1k.jpg",
        "size": 1323911,
        "md5": "c6b4962593e905353e66937ad392a4ca"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/1k/forest_ground_04/forest_ground_04_nor_dx_1k.png",
        "size": 2537895,
        "md5": "17d3c9b519ab281d57723a53c9c888e7"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/1k/forest_ground_04/forest_ground_04_nor_dx_1k.exr",
        "size": 2586564,
        "md5": "e4edae419f30f730a8825201bd185eeb"
      }
    }
  },
  "blend": {
    "8k": {
      "blend": {
        "include": {
          "textures/forest_ground_04_disp_8k.png": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/8k/forest_ground_04/forest_ground_04_disp_8k.png",
            "size": 50448735,
            "md5": "ee8ced66a60a44b635916c28057a7300"
          },
          "textures/forest_ground_04_nor_gl_8k.exr": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/8k/forest_ground_04/forest_ground_04_nor_gl_8k.exr",
            "size": 123734046,
            "md5": "aeb39f906738660eb319876cce3c8950"
          },
          "textures/forest_ground_04_rough_8k.exr": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/8k/forest_ground_04/forest_ground_04_rough_8k.exr",
            "size": 42237469,
            "md5": "cd9b83132b44a9a83f25c44deb68f562"
          },
          "textures/forest_ground_04_diff_8k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/8k/forest_ground_04/forest_ground_04_diff_8k.jpg",
            "size": 51978989,
            "md5": "dc01af35ee412f428e34371f00f7f640"
          }
        },
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/blend/8k/forest_ground_04/forest_ground_04_8k.blend",
        "size": 230109,
        "md5": "b2bef2646e986bfe18f43da3dcee8c81"
      }
    },
    "4k": {
      "blend": {
        "include": {
          "textures/forest_ground_04_nor_gl_4k.exr": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/4k/forest_ground_04/forest_ground_04_nor_gl_4k.exr",
            "size": 37696008,
            "md5": "b38a72456672555acff46fc54a9d95c5"
          },
          "textures/forest_ground_04_diff_4k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/4k/forest_ground_04/forest_ground_04_diff_4k.jpg",
            "size": 16611792,
            "md5": "68d76337ff8aebe1853dd20783b26008"
          },
          "textures/forest_ground_04_rough_4k.exr": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/4k/forest_ground_04/forest_ground_04_rough_4k.exr",
            "size": 12848299,
            "md5": "875d2ec8a7587d3d96b1f3f189b244ad"
          },
          "textures/forest_ground_04_disp_4k.png": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/4k/forest_ground_04/forest_ground_04_disp_4k.png",
            "size": 20789218,
            "md5": "17fe418956c73e204cb162c2686c4466"
          }
        },
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/blend/4k/forest_ground_04/forest_ground_04_4k.blend",
        "size": 229486,
        "md5": "1024deb159a92149bbf54e1feeb966aa"
      }
    },
    "2k": {
      "blend": {
        "include": {
          "textures/forest_ground_04_diff_2k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/forest_ground_04/forest_ground_04_diff_2k.jpg",
            "size": 4529321,
            "md5": "f6ce9c8af4316c2627a4b3fbab8ad944"
          },
          "textures/forest_ground_04_nor_gl_2k.exr": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/2k/forest_ground_04/forest_ground_04_nor_gl_2k.exr",
            "size": 10166059,
            "md5": "1a60084f31c279f8a8c52824835a6883"
          },
          "textures/forest_ground_04_disp_2k.png": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/2k/forest_ground_04/forest_ground_04_disp_2k.png",
            "size": 5851483,
            "md5": "aee602747c99de3ecee2c4d969775aaf"
          },
          "textures/forest_ground_04_rough_2k.exr": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/2k/forest_ground_04/forest_ground_04_rough_2k.exr",
            "size": 3477073,
            "md5": "6dc326d383797eec8f3c5d239776e299"
          }
        },
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/blend/2k/forest_ground_04/forest_ground_04_2k.blend",
        "size": 229557,
        "md5": "74ffdd894e16566653be7a9699c12a38"
      }
    },
    "1k": {
      "blend": {
        "include": {
          "textures/forest_ground_04_nor_gl_1k.exr": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/1k/forest_ground_04/forest_ground_04_nor_gl_1k.exr",
            "size": 2588522,
            "md5": "b670ed5e33c011d5ef289be72c070139"
          },
          "textures/forest_ground_04_diff_1k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/forest_ground_04/forest_ground_04_diff_1k.jpg",
            "size": 1113899,
            "md5": "6ad9df4d731a238299806f739a26af83"
          },
          "textures/forest_ground_04_disp_1k.png": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/1k/forest_ground_04/forest_ground_04_disp_1k.png",
            "size": 1548354,
            "md5": "fb41f8f219e76d6a52095f12bb3c20b2"
          },
          "textures/forest_ground_04_rough_1k.exr": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/1k/forest_ground_04/forest_ground_04_rough_1k.exr",
            "size": 880797,
            "md5": "55ddd8ccf5fd3750f7f6836cd886dd71"
          }
        },
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/blend/1k/forest_ground_04/forest_ground_04_1k.blend",
        "size": 229888,
        "md5": "414cdec51845309e9de7a86d5fa0172a"
      }
    }
  },
  "gltf": {
    "8k": {
      "gltf": {
        "include": {
          "forest_ground_04.bin": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/gltf/8k/forest_ground_04/forest_ground_04.bin",
            "size": 2369652,
            "md5": "fdc805a2b5e09b0f8c547a927a1a2a4e"
          },
          "textures/forest_ground_04_nor_gl_8k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/8k/forest_ground_04/forest_ground_04_nor_gl_8k.jpg",
            "size": 64622903,
            "md5": "f3f930a4b81a2598bf5defef8596c818"
          },
          "textures/forest_ground_04_rough_8k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/8k/forest_ground_04/forest_ground_04_rough_8k.jpg",
            "size": 27523437,
            "md5": "43856d4040e40f66afa2c13aabae17e9"
          },
          "textures/forest_ground_04_diff_8k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/8k/forest_ground_04/forest_ground_04_diff_8k.jpg",
            "size": 51978989,
            "md5": "dc01af35ee412f428e34371f00f7f640"
          }
        },
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/gltf/8k/forest_ground_04/forest_ground_04_8k.gltf",
        "size": 2732,
        "md5": "46e1da217101c1d20156016a6f82ddee"
      }
    },
    "4k": {
      "gltf": {
        "include": {
          "textures/forest_ground_04_diff_4k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/4k/forest_ground_04/forest_ground_04_diff_4k.jpg",
            "size": 16611792,
            "md5": "68d76337ff8aebe1853dd20783b26008"
          },
          "textures/forest_ground_04_rough_4k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/4k/forest_ground_04/forest_ground_04_rough_4k.jpg",
            "size": 9043240,
            "md5": "2d411d592d68a1081fefe8eb6e453383"
          },
          "textures/forest_ground_04_nor_gl_4k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/4k/forest_ground_04/forest_ground_04_nor_gl_4k.jpg",
            "size": 19420073,
            "md5": "b0241bdb8f7a3cb6b9bfe9ccc01903a0"
          },
          "forest_ground_04.bin": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/gltf/8k/forest_ground_04/forest_ground_04.bin",
            "size": 2369652,
            "md5": "fdc805a2b5e09b0f8c547a927a1a2a4e"
          }
        },
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/gltf/4k/forest_ground_04/forest_ground_04_4k.gltf",
        "size": 2732,
        "md5": "f70084dd43103334e6f5710a842305f9"
      }
    },
    "2k": {
      "gltf": {
        "include": {
          "textures/forest_ground_04_diff_2k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/forest_ground_04/forest_ground_04_diff_2k.jpg",
            "size": 4529321,
            "md5": "f6ce9c8af4316c2627a4b3fbab8ad944"
          },
          "textures/forest_ground_04_rough_2k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/forest_ground_04/forest_ground_04_rough_2k.jpg",
            "size": 2509805,
            "md5": "81daa9a1d8b35dd9ffdff3aa7a74fbd9"
          },
          "textures/forest_ground_04_nor_gl_2k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/forest_ground_04/forest_ground_04_nor_gl_2k.jpg",
            "size": 5288849,
            "md5": "5fa9256955e7bfa40a0269b4965ce646"
          },
          "forest_ground_04.bin": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/gltf/8k/forest_ground_04/forest_ground_04.bin",
            "size": 2369652,
            "md5": "fdc805a2b5e09b0f8c547a927a1a2a4e"
          }
        },
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/gltf/2k/forest_ground_04/forest_ground_04_2k.gltf",
        "size": 2732,
        "md5": "8d2ce666262c34b4c0d5d9c7139b519a"
      }
    },
    "1k": {
      "gltf": {
        "include": {
          "textures/forest_ground_04_diff_1k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/forest_ground_04/forest_ground_04_diff_1k.jpg",
            "size": 1113899,
            "md5": "6ad9df4d731a238299806f739a26af83"
          },
          "textures/forest_ground_04_nor_gl_1k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/forest_ground_04/forest_ground_04_nor_gl_1k.jpg",
            "size": 1326812,
            "md5": "a010a0802c2d9a930c6f00d3b1f196d2"
          },
          "textures/forest_ground_04_rough_1k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/forest_ground_04/forest_ground_04_rough_1k.jpg",
            "size": 627507,
            "md5": "b5c7ba5c16f00261e9a765d52cf7e0ac"
          },
          "forest_ground_04.bin": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/gltf/8k/forest_ground_04/forest_ground_04.bin",
            "size": 2369652,
            "md5": "fdc805a2b5e09b0f8c547a927a1a2a4e"
          }
        },
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/gltf/1k/forest_ground_04/forest_ground_04_1k.gltf",
        "size": 2732,
        "md5": "546d3805fdfe3b3a7d76ff8870e25e32"
      }
    }
  },
  "Rough": {
    "8k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/8k/forest_ground_04/forest_ground_04_rough_8k.jpg",
        "size": 27523437,
        "md5": "43856d4040e40f66afa2c13aabae17e9"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/8k/forest_ground_04/forest_ground_04_rough_8k.png",
        "size": 29271192,
        "md5": "331da8343624d34a8e68ccff780a09f8"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/8k/forest_ground_04/forest_ground_04_rough_8k.exr",
        "size": 42237469,
        "md5": "cd9b83132b44a9a83f25c44deb68f562"
      }
    },
    "4k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/4k/forest_ground_04/forest_ground_04_rough_4k.jpg",
        "size": 9043240,
        "md5": "2d411d592d68a1081fefe8eb6e453383"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/4k/forest_ground_04/forest_ground_04_rough_4k.png",
        "size": 9552055,
        "md5": "44ded6d079237fd8782c50057f5928b7"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/4k/forest_ground_04/forest_ground_04_rough_4k.exr",
        "size": 12848299,
        "md5": "875d2ec8a7587d3d96b1f3f189b244ad"
      }
    },
    "2k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/forest_ground_04/forest_ground_04_rough_2k.jpg",
        "size": 2509805,
        "md5": "81daa9a1d8b35dd9ffdff3aa7a74fbd9"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/2k/forest_ground_04/forest_ground_04_rough_2k.png",
        "size": 2610027,
        "md5": "49bd9e53639081da4b8bda9163c469b5"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/2k/forest_ground_04/forest_ground_04_rough_2k.exr",
        "size": 3477073,
        "md5": "6dc326d383797eec8f3c5d239776e299"
      }
    },
    "1k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/forest_ground_04/forest_ground_04_rough_1k.jpg",
        "size": 627507,
        "md5": "b5c7ba5c16f00261e9a765d52cf7e0ac"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/1k/forest_ground_04/forest_ground_04_rough_1k.png",
        "size": 661111,
        "md5": "d9a63bc30621bf8edd1a3a44d2245a16"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/1k/forest_ground_04/forest_ground_04_rough_1k.exr",
        "size": 880797,
        "md5": "55ddd8ccf5fd3750f7f6836cd886dd71"
      }
    }
  },
  "Displacement": {
    "8k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/8k/forest_ground_04/forest_ground_04_disp_8k.jpg",
        "size": 5415766,
        "md5": "97652eac177e5e402128c796884c00d1"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/8k/forest_ground_04/forest_ground_04_disp_8k.png",
        "size": 50448735,
        "md5": "ee8ced66a60a44b635916c28057a7300"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/8k/forest_ground_04/forest_ground_04_disp_8k.exr",
        "size": 26406630,
        "md5": "732400190558b896860ce2a1c23d7177"
      }
    },
    "4k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/4k/forest_ground_04/forest_ground_04_disp_4k.jpg",
        "size": 2016873,
        "md5": "0bff37428785a95fcea9e8f5a379a25a"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/4k/forest_ground_04/forest_ground_04_disp_4k.png",
        "size": 20789218,
        "md5": "17fe418956c73e204cb162c2686c4466"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/4k/forest_ground_04/forest_ground_04_disp_4k.exr",
        "size": 8740562,
        "md5": "1c8736b358795d69407dbabaa038c141"
      }
    },
    "2k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/forest_ground_04/forest_ground_04_disp_2k.jpg",
        "size": 696276,
        "md5": "2d342b36619dc1a4e4afcd3bfc256dfe"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/2k/forest_ground_04/forest_ground_04_disp_2k.png",
        "size": 5851483,
        "md5": "aee602747c99de3ecee2c4d969775aaf"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/2k/forest_ground_04/forest_ground_04_disp_2k.exr",
        "size": 2799474,
        "md5": "40a1d935eed9e6eb61c6646d8cdd39a5"
      }
    },
    "1k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/forest_ground_04/forest_ground_04_disp_1k.jpg",
        "size": 223545,
        "md5": "bc4bc69e809d50f2a662f5a48bd2a2aa"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/1k/forest_ground_04/forest_ground_04_disp_1k.png",
        "size": 1548354,
        "md5": "fb41f8f219e76d6a52095f12bb3c20b2"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/1k/forest_ground_04/forest_ground_04_disp_1k.exr",
        "size": 811077,
        "md5": "ef035d2066819feb615495502c3c1689"
      }
    }
  },
  "AO": {
    "8k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/8k/forest_ground_04/forest_ground_04_ao_8k.jpg",
        "size": 30687064,
        "md5": "790ea3c6169a08bd7031508974d8a8d5"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/8k/forest_ground_04/forest_ground_04_ao_8k.png",
        "size": 50644730,
        "md5": "d67726c009f1cf7962233e7b1d14388e"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/8k/forest_ground_04/forest_ground_04_ao_8k.exr",
        "size": 29937802,
        "md5": "474c0158943b3cb959acdf0d09abb325"
      }
    },
    "4k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/4k/forest_ground_04/forest_ground_04_ao_4k.jpg",
        "size": 9589603,
        "md5": "98916cf2580deefe99bb8530c3835fe1"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/4k/forest_ground_04/forest_ground_04_ao_4k.png",
        "size": 14941770,
        "md5": "7beaf26750b2740e86800cc8db58904a"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/4k/forest_ground_04/forest_ground_04_ao_4k.exr",
        "size": 9566284,
        "md5": "c721b37d67b5082ab598d32e49db9675"
      }
    },
    "2k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/forest_ground_04/forest_ground_04_ao_2k.jpg",
        "size": 2950026,
        "md5": "c86e235729064b28e2354bf4bdcd4465"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/2k/forest_ground_04/forest_ground_04_ao_2k.png",
        "size": 4278431,
        "md5": "49b61fd20cf02989245569c39100a433"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/2k/forest_ground_04/forest_ground_04_ao_2k.exr",
        "size": 2950733,
        "md5": "bbe945bbf8982acdc20e16b61c2491aa"
      }
    },
    "1k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/forest_ground_04/forest_ground_04_ao_1k.jpg",
        "size": 831864,
        "md5": "289bfcedb4676de2632c678a35eeea63"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/1k/forest_ground_04/forest_ground_04_ao_1k.png",
        "size": 1138638,
        "md5": "91fc65dbf2a0110c87574d952673ef3a"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/1k/forest_ground_04/forest_ground_04_ao_1k.exr",
        "size": 835367,
        "md5": "c57a7758a1e1528fca7b463438046597"
      }
    }
  },
  "nor_gl": {
    "8k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/8k/forest_ground_04/forest_ground_04_nor_gl_8k.jpg",
        "size": 64622903,
        "md5": "f3f930a4b81a2598bf5defef8596c818"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/8k/forest_ground_04/forest_ground_04_nor_gl_8k.png",
        "size": 126199103,
        "md5": "65e9e14f7ee42657b6e7d5d023870e3f"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/8k/forest_ground_04/forest_ground_04_nor_gl_8k.exr",
        "size": 123734046,
        "md5": "aeb39f906738660eb319876cce3c8950"
      }
    },
    "4k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/4k/forest_ground_04/forest_ground_04_nor_gl_4k.jpg",
        "size": 19420073,
        "md5": "b0241bdb8f7a3cb6b9bfe9ccc01903a0"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/4k/forest_ground_04/forest_ground_04_nor_gl_4k.png",
        "size": 37822518,
        "md5": "0dbcfa2a379e0b10dbc01151099764b2"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/4k/forest_ground_04/forest_ground_04_nor_gl_4k.exr",
        "size": 37696008,
        "md5": "b38a72456672555acff46fc54a9d95c5"
      }
    },
    "2k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/forest_ground_04/forest_ground_04_nor_gl_2k.jpg",
        "size": 5288849,
        "md5": "5fa9256955e7bfa40a0269b4965ce646"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/2k/forest_ground_04/forest_ground_04_nor_gl_2k.png",
        "size": 9987026,
        "md5": "c7bf8a98de1dee39cb6028e06b8bdd37"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/2k/forest_ground_04/forest_ground_04_nor_gl_2k.exr",
        "size": 10166059,
        "md5": "1a60084f31c279f8a8c52824835a6883"
      }
    },
    "1k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/forest_ground_04/forest_ground_04_nor_gl_1k.jpg",
        "size": 1326812,
        "md5": "a010a0802c2d9a930c6f00d3b1f196d2"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/1k/forest_ground_04/forest_ground_04_nor_gl_1k.png",
        "size": 2537954,
        "md5": "77c2337c8c8c47357f69b598e73a72de"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/1k/forest_ground_04/forest_ground_04_nor_gl_1k.exr",
        "size": 2588522,
        "md5": "b670ed5e33c011d5ef289be72c070139"
      }
    }
  },
  "arm": {
    "8k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/8k/forest_ground_04/forest_ground_04_arm_8k.jpg",
        "size": 38759570,
        "md5": "8ca760c1f90feb72456620f0c62fb1ee"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/8k/forest_ground_04/forest_ground_04_arm_8k.png",
        "size": 75237657,
        "md5": "f1dc38bbed63d15b76da3596d3726255"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/8k/forest_ground_04/forest_ground_04_arm_8k.exr",
        "size": 61198147,
        "md5": "47c4b8e7029c019ec52d47bd43cecb16"
      }
    },
    "4k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/4k/forest_ground_04/forest_ground_04_arm_4k.jpg",
        "size": 12202613,
        "md5": "351fc373d8816b52a355cead709a8532"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/4k/forest_ground_04/forest_ground_04_arm_4k.png",
        "size": 23612724,
        "md5": "909571bcb87e70a1095c1a2e44a2a2c9"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/4k/forest_ground_04/forest_ground_04_arm_4k.exr",
        "size": 21510823,
        "md5": "3b8e2e1519ae2e5e7f9ad5c15c193bc0"
      }
    },
    "2k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/forest_ground_04/forest_ground_04_arm_2k.jpg",
        "size": 3418726,
        "md5": "c688030cc91fbaab71cbc71fc6b280fe"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/2k/forest_ground_04/forest_ground_04_arm_2k.png",
        "size": 6575224,
        "md5": "85449b5940ae70ddc7fb73a6aecd7543"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/2k/forest_ground_04/forest_ground_04_arm_2k.exr",
        "size": 6487686,
        "md5": "6a137957146e6abcbe0405625118cbf5"
      }
    },
    "1k": {
      "jpg": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/forest_ground_04/forest_ground_04_arm_1k.jpg",
        "size": 882694,
        "md5": "3fbb079d961f59b8fac7ec4f6dab9f20"
      },
      "png": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/1k/forest_ground_04/forest_ground_04_arm_1k.png",
        "size": 1729224,
        "md5": "78205b87e70677c052f5538ca5f1e8ff"
      },
      "exr": {
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/1k/forest_ground_04/forest_ground_04_arm_1k.exr",
        "size": 1729917,
        "md5": "bf5c42abf67867620a3841261728a77b"
      }
    }
  },
  "mtlx": {
    "8k": {
      "mtlx": {
        "include": {
          "textures/forest_ground_04_disp_8k.png": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/8k/forest_ground_04/forest_ground_04_disp_8k.png",
            "size": 50448735,
            "md5": "ee8ced66a60a44b635916c28057a7300"
          },
          "textures/forest_ground_04_nor_gl_8k.exr": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/8k/forest_ground_04/forest_ground_04_nor_gl_8k.exr",
            "size": 123734046,
            "md5": "aeb39f906738660eb319876cce3c8950"
          },
          "textures/forest_ground_04_rough_8k.exr": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/8k/forest_ground_04/forest_ground_04_rough_8k.exr",
            "size": 42237469,
            "md5": "cd9b83132b44a9a83f25c44deb68f562"
          },
          "textures/forest_ground_04_diff_8k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/8k/forest_ground_04/forest_ground_04_diff_8k.jpg",
            "size": 51978989,
            "md5": "dc01af35ee412f428e34371f00f7f640"
          }
        },
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/mtlx/8k/forest_ground_04/forest_ground_04_8k.mtlx",
        "size": 4755,
        "md5": "e2af5b637e7afee44df0b516e99a62ea"
      }
    },
    "4k": {
      "mtlx": {
        "include": {
          "textures/forest_ground_04_nor_gl_4k.exr": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/4k/forest_ground_04/forest_ground_04_nor_gl_4k.exr",
            "size": 37696008,
            "md5": "b38a72456672555acff46fc54a9d95c5"
          },
          "textures/forest_ground_04_diff_4k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/4k/forest_ground_04/forest_ground_04_diff_4k.jpg",
            "size": 16611792,
            "md5": "68d76337ff8aebe1853dd20783b26008"
          },
          "textures/forest_ground_04_rough_4k.exr": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/4k/forest_ground_04/forest_ground_04_rough_4k.exr",
            "size": 12848299,
            "md5": "875d2ec8a7587d3d96b1f3f189b244ad"
          },
          "textures/forest_ground_04_disp_4k.png": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/4k/forest_ground_04/forest_ground_04_disp_4k.png",
            "size": 20789218,
            "md5": "17fe418956c73e204cb162c2686c4466"
          }
        },
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/mtlx/4k/forest_ground_04/forest_ground_04_4k.mtlx",
        "size": 4755,
        "md5": "2a1fa58d53ad1d4d6a37dbb000c7ab9f"
      }
    },
    "2k": {
      "mtlx": {
        "include": {
          "textures/forest_ground_04_diff_2k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/forest_ground_04/forest_ground_04_diff_2k.jpg",
            "size": 4529321,
            "md5": "f6ce9c8af4316c2627a4b3fbab8ad944"
          },
          "textures/forest_ground_04_nor_gl_2k.exr": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/2k/forest_ground_04/forest_ground_04_nor_gl_2k.exr",
            "size": 10166059,
            "md5": "1a60084f31c279f8a8c52824835a6883"
          },
          "textures/forest_ground_04_disp_2k.png": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/2k/forest_ground_04/forest_ground_04_disp_2k.png",
            "size": 5851483,
            "md5": "aee602747c99de3ecee2c4d969775aaf"
          },
          "textures/forest_ground_04_rough_2k.exr": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/2k/forest_ground_04/forest_ground_04_rough_2k.exr",
            "size": 3477073,
            "md5": "6dc326d383797eec8f3c5d239776e299"
          }
        },
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/mtlx/2k/forest_ground_04/forest_ground_04_2k.mtlx",
        "size": 4755,
        "md5": "0a1b20a6c0f56db2037a75b0f41b321d"
      }
    },
    "1k": {
      "mtlx": {
        "include": {
          "textures/forest_ground_04_nor_gl_1k.exr": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/1k/forest_ground_04/forest_ground_04_nor_gl_1k.exr",
            "size": 2588522,
            "md5": "b670ed5e33c011d5ef289be72c070139"
          },
          "textures/forest_ground_04_diff_1k.jpg": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/forest_ground_04/forest_ground_04_diff_1k.jpg",
            "size": 1113899,
            "md5": "6ad9df4d731a238299806f739a26af83"
          },
          "textures/forest_ground_04_disp_1k.png": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/png/1k/forest_ground_04/forest_ground_04_disp_1k.png",
            "size": 1548354,
            "md5": "fb41f8f219e76d6a52095f12bb3c20b2"
          },
          "textures/forest_ground_04_rough_1k.exr": {
            "url": "https://dl.polyhaven.org/file/ph-assets/Textures/exr/1k/forest_ground_04/forest_ground_04_rough_1k.exr",
            "size": 880797,
            "md5": "55ddd8ccf5fd3750f7f6836cd886dd71"
          }
        },
        "url": "https://dl.polyhaven.org/file/ph-assets/Textures/mtlx/1k/forest_ground_04/forest_ground_04_1k.mtlx",
        "size": 4755,
        "md5": "ee42833ca430b69ae455ad2caa95f242"
      }
    }
  }
}
```

## forest-ground-source.json

```json
{
  "source": "https://polyhaven.com/a/forest_ground_04",
  "license": "CC0",
  "license_url": "https://polyhaven.com/license",
  "authors": {
    "Rob Tuytel": "Photography, Processing",
    "Rico Cilliers": "Minor adjustment"
  },
  "files": [
    {
      "role": "diff",
      "file": "assets/polyhaven/forest_ground_04/forest_ground_04_diff_2k.jpg",
      "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/forest_ground_04/forest_ground_04_diff_2k.jpg",
      "bytes": 4529321,
      "sha256": "24b8aaf4c8547305d80b0e029ec365219ab1b90748d4ca5651bdc9e75bfde5e6"
    },
    {
      "role": "nor_gl",
      "file": "assets/polyhaven/forest_ground_04/forest_ground_04_nor_gl_2k.jpg",
      "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/forest_ground_04/forest_ground_04_nor_gl_2k.jpg",
      "bytes": 5288849,
      "sha256": "b37ac799ed410976541e0011c246fca254e74eaa3f096ae7a7d74178db5a6d9b"
    },
    {
      "role": "rough",
      "file": "assets/polyhaven/forest_ground_04/forest_ground_04_rough_2k.jpg",
      "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/forest_ground_04/forest_ground_04_rough_2k.jpg",
      "bytes": 2509805,
      "sha256": "24194662948424c323c75e0918461ad9d38bc0a275022000144f248b3efb4119"
    }
  ]
}
```

## grass-ground-source.json

```json
{
  "source": "https://polyhaven.com/a/leafy_grass",
  "license": "CC0",
  "license_url": "https://polyhaven.com/license",
  "authors": {
    "Charlotte Baglioni": "All"
  },
  "files": [
    {
      "role": "diff",
      "file": "assets/polyhaven/leafy_grass/leafy_grass_diff_2k.jpg",
      "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/leafy_grass/leafy_grass_diff_2k.jpg",
      "bytes": 4766204,
      "sha256": "8e1c6d21365d4b89bc5a35ab664da98a78dbc3ab9ba6100474881c8619a5b113"
    },
    {
      "role": "nor_gl",
      "file": "assets/polyhaven/leafy_grass/leafy_grass_nor_gl_2k.jpg",
      "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/leafy_grass/leafy_grass_nor_gl_2k.jpg",
      "bytes": 6047262,
      "sha256": "df0cf0ce96e653f033e5b934d5b12995464bda027aa53bd12e329be889aa9f45"
    },
    {
      "role": "rough",
      "file": "assets/polyhaven/leafy_grass/leafy_grass_rough_2k.jpg",
      "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/leafy_grass/leafy_grass_rough_2k.jpg",
      "bytes": 2274652,
      "sha256": "34e1733bf4064b6950a575ff57e78c8b7ff71b63749eb48a49221bd65de6fb4f"
    }
  ]
}
```

## reused-materials.json

```json
[
  {
    "source_local": "/Users/mark.ma/Documents/AI coding/zenflow/docs/prototypes/reading-room/assets/white-oak/diff.jpg",
    "file": "assets/polyhaven/reused-white-oak/diff.jpg",
    "sha256": "8fdec14fd3c06de907693c9c3241037dc93327ad907296371a2f74a0becd8187",
    "license": "CC0",
    "source": "https://polyhaven.com/a/white_oak_veneer"
  },
  {
    "source_local": "/Users/mark.ma/Documents/AI coding/zenflow/docs/prototypes/reading-room/assets/white-oak/nor_gl.jpg",
    "file": "assets/polyhaven/reused-white-oak/nor_gl.jpg",
    "sha256": "9e8dd65e443d92107caa4cd30403d78946a8b8c5a6029e6446c7368865b1d1c0",
    "license": "CC0",
    "source": "https://polyhaven.com/a/white_oak_veneer"
  },
  {
    "source_local": "/Users/mark.ma/Documents/AI coding/zenflow/docs/prototypes/reading-room/assets/white-oak/rough.jpg",
    "file": "assets/polyhaven/reused-white-oak/rough.jpg",
    "sha256": "a8670e80f203bd031ac3c80f0f606383be51a0ee108e016fa19cad6b9bd373e7",
    "license": "CC0",
    "source": "https://polyhaven.com/a/white_oak_veneer"
  },
  {
    "file": "assets/textures/leaf-photo-cc0.png",
    "derived_from": "tree_small_02_leaves_diff_1k.png",
    "source": "https://polyhaven.com/a/tree_small_02",
    "license": "CC0",
    "modification": "single leaf atlas crop and resample; original alpha retained",
    "author": "Rico Cilliers"
  }
]
```

## source-register.json

```json
{
  "source_project": "/Users/mark.ma/Documents/AI coding/zenflow",
  "project": "/Users/mark.ma/Documents/AI coding/zenflow-space",
  "copied_on": "2026-10-20",
  "files": [
    {
      "path": "docs/design/禅意空间-设计总纲-v1.2-Blender制作版-20261020.md",
      "sha256": "c5e6741ca60cecc1ec39b1d852c092253eac3ccbedce5e2035b3e93c7db79092",
      "bytes": 93515
    },
    {
      "path": "docs/design/空间尺度与入场视角修订-20261020.md",
      "sha256": "e4dbf1a393c8559d18ab9d0a19d6745c97b16f0f4d456b8ff3924f1a8f5cc222",
      "bytes": 18095
    },
    {
      "path": "docs/design/Blender技能评审与选型-20261020.md",
      "sha256": "5bf5e21d4594941c72a6671d4ecc688ebf5a4083377ee72b8ca1817b1d65005d",
      "bytes": 10250
    },
    {
      "path": "docs/design/style-studies-20261020/v3/03-entry-tang.png",
      "sha256": "9879e7d910cbadced261b3e3404af12edbc2cf3b7c6b47d3d87285dc344ff969",
      "bytes": 2808368
    },
    {
      "path": "docs/design/style-studies-20261020/v7/02-dialogue-cottage-entry.png",
      "sha256": "8289d3911caa71b33700e5bea3671b1405b2186cdb5c5f735ebce240dc50cb33",
      "bytes": 2749986
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/scene-briefs.json",
      "sha256": "18bbfe18712472c2bbb47926ae86500fce478730dfcc8278e93a2faf9e1d64a2",
      "bytes": 27844
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/02-water-garden-bridge.png",
      "sha256": "203d884f5335f9b4e0d735c9bd0cda5bf468d6006e4a8bd2b9f268ed8969f8d3",
      "bytes": 3869548
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/00-overview-courtyard-blossoms.png",
      "sha256": "6c92196bbb703e1c3f2b55ab58d6bedaf50323a070a253eb0e16bd6a21ef90b7",
      "bytes": 3286883
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/08-wish-tree-platform.png",
      "sha256": "3866a8b87c8a35a6a5fae4222c5aa8d1311361e7a5f9100a93cdc53a77dc843b",
      "bytes": 3552437
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/11-conversation-cottage-seat.png",
      "sha256": "1b3a6fb097a7b4312fd1dbcc9a5b9ee247188f5f787f6e5b0d5795699802c49b",
      "bytes": 2647120
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/06-private-side-room.png",
      "sha256": "0e6eae12105b3f85320e62b9bc5dbe468f1fae1e93333d8863021dbaad0e5c1a",
      "bytes": 2619851
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/01-waterside-veranda.png",
      "sha256": "9b4df698ffa1f341490a4e4242d1a3c74266d6d702b8fac93a5e74e72525435c",
      "bytes": 2916817
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/09-old-tree-path.png",
      "sha256": "07581de922b69cf67be7e655f27f39ec188dac801fceb32714634708360f01c9",
      "bytes": 3656823
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/07-bell-court.png",
      "sha256": "d31f69a03bc6f4f5bfcd78aaca9c4d15158033344d07cd2f205768561404b39b",
      "bytes": 2992388
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/13-bookshelf-select.png",
      "sha256": "a225e25b680a1b8971d282dc0d9cc26e049169b9772d17550e500c0e797f18a8",
      "bytes": 2357587
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/00-overview-meditation-cushion.png",
      "sha256": "5946f725ce9a13f4e5d809ef7dfd08bec4f2273ea36fc840d4961548065f0335",
      "bytes": 3371410
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/README.md",
      "sha256": "fd4d5510e6cddbd95e87e9e1355fa16e827a64b3d8fcda0ebff9e6ee979187dd",
      "bytes": 7325
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/12-front-court-threshold.png",
      "sha256": "f8d369bdd4a35c659cf51e328765a370eb4dbf083fe0b9a82bb58ba0589edeeb",
      "bytes": 2759226
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/03-island-meditation.png",
      "sha256": "389f9c5c8dd1b74f558041b7636a28ec629ea232fe3eb158330f8b0358b4926a",
      "bytes": 3584191
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/image-sources.json",
      "sha256": "955d47ea37c26757b1846b3afecc0e43b2c7512bc4377f12e3f34d1152be3924",
      "bytes": 3270
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/05-copy-hall-seated.png",
      "sha256": "88e74def71328823e25aad193e1cf8baaa5785b7a777bb80f0ae39677d0dcd09",
      "bytes": 2121178
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/04-library-seat.png",
      "sha256": "d63a06b70ee5d02588a0cea9a8c8309c9eb37486bc24b079ff32feed59f60a8b",
      "bytes": 2702571
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/prompts.md",
      "sha256": "317941e5566f3bb00b3cb0cdd2123df49ca761bf813065f919c9e9f76d2c2e2c",
      "bytes": 26583
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/10-right-lookout-dawn.png",
      "sha256": "98fe778547750a041ba4979e09eb47a48854a07e453646ef2d1ecc931dadbd4a",
      "bytes": 2752523
    },
    {
      "path": "docs/design/style-studies-20261020/v8-first-person/references/user-reader-reference.png",
      "sha256": "544c2ae3fa7a3a4b64d8bd0cacfbf450cf21425c6a7d4c299ed6efb35bbe5b4a",
      "bytes": 90689
    }
  ]
}
```

## third-party-assets.json

```json
{
  "license_page": "https://polyhaven.com/license",
  "api_terms": "https://github.com/Poly-Haven/Public-API/blob/master/ToS.md",
  "fetched": "2026-10-20",
  "files": [
    {
      "asset": "kloofendal_48d_partly_cloudy_puresky",
      "map": "hdri",
      "resolution": "2k",
      "file": "assets/polyhaven/kloofendal_48d_partly_cloudy_puresky_2k.hdr",
      "url": "https://dl.polyhaven.org/file/ph-assets/HDRIs/hdr/2k/kloofendal_48d_partly_cloudy_puresky_2k.hdr",
      "md5": "2eba3a4d7eeb23cbfbeca364c97e7980",
      "size": 5451493,
      "license": "CC0",
      "source_page": "https://polyhaven.com/a/kloofendal_48d_partly_cloudy_puresky"
    },
    {
      "asset": "wood_floor_deck",
      "map": "Diffuse",
      "resolution": "2k",
      "file": "assets/polyhaven/wood_floor_deck_diff_2k.jpg",
      "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/wood_floor_deck/wood_floor_deck_diff_2k.jpg",
      "md5": "c272fb52a326a8724a409bdb33db1ba0",
      "size": 3804436,
      "license": "CC0",
      "source_page": "https://polyhaven.com/a/wood_floor_deck"
    },
    {
      "asset": "wood_floor_deck",
      "map": "nor_gl",
      "resolution": "2k",
      "file": "assets/polyhaven/wood_floor_deck_nor_gl_2k.jpg",
      "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/wood_floor_deck/wood_floor_deck_nor_gl_2k.jpg",
      "md5": "4c4b56a2db12514e0e96abe57a4f4d03",
      "size": 3121998,
      "license": "CC0",
      "source_page": "https://polyhaven.com/a/wood_floor_deck"
    },
    {
      "asset": "wood_floor_deck",
      "map": "Rough",
      "resolution": "2k",
      "file": "assets/polyhaven/wood_floor_deck_rough_2k.jpg",
      "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/wood_floor_deck/wood_floor_deck_rough_2k.jpg",
      "md5": "96df63006351aa77d350a9a11fb5cc78",
      "size": 2881541,
      "license": "CC0",
      "source_page": "https://polyhaven.com/a/wood_floor_deck"
    },
    {
      "asset": "wood_floor_deck",
      "map": "Displacement",
      "resolution": "1k",
      "file": "assets/polyhaven/wood_floor_deck_disp_1k.jpg",
      "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/wood_floor_deck/wood_floor_deck_disp_1k.jpg",
      "md5": "6b498dfb70a191483b144b2a41c8cac1",
      "size": 233901,
      "license": "CC0",
      "source_page": "https://polyhaven.com/a/wood_floor_deck"
    },
    {
      "asset": "rock_boulder_cracked",
      "map": "Diffuse",
      "resolution": "2k",
      "file": "assets/polyhaven/rock_boulder_cracked_diff_2k.jpg",
      "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/rock_boulder_cracked/rock_boulder_cracked_diff_2k.jpg",
      "md5": "91b7d0b3d18b1d7b7705cd493149c969",
      "size": 3962095,
      "license": "CC0",
      "source_page": "https://polyhaven.com/a/rock_boulder_cracked"
    },
    {
      "asset": "rock_boulder_cracked",
      "map": "nor_gl",
      "resolution": "2k",
      "file": "assets/polyhaven/rock_boulder_cracked_nor_gl_2k.jpg",
      "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/rock_boulder_cracked/rock_boulder_cracked_nor_gl_2k.jpg",
      "md5": "978143a19ebc1a6acea5c4c52e954b9b",
      "size": 4144084,
      "license": "CC0",
      "source_page": "https://polyhaven.com/a/rock_boulder_cracked"
    },
    {
      "asset": "rock_boulder_cracked",
      "map": "Rough",
      "resolution": "2k",
      "file": "assets/polyhaven/rock_boulder_cracked_rough_2k.jpg",
      "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/rock_boulder_cracked/rock_boulder_cracked_rough_2k.jpg",
      "md5": "750c41770beb04180ee936a45c15aecf",
      "size": 1680734,
      "license": "CC0",
      "source_page": "https://polyhaven.com/a/rock_boulder_cracked"
    },
    {
      "asset": "rock_boulder_cracked",
      "map": "Displacement",
      "resolution": "1k",
      "file": "assets/polyhaven/rock_boulder_cracked_disp_1k.jpg",
      "url": "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/rock_boulder_cracked/rock_boulder_cracked_disp_1k.jpg",
      "md5": "72ffeb8afc2bcac39a2c55678827607e",
      "size": 213877,
      "license": "CC0",
      "source_page": "https://polyhaven.com/a/rock_boulder_cracked"
    }
  ]
}
```

## tree-asset-source.json

```json
[
  {
    "file": "assets/polyhaven/tree_small_02/textures/tree_small_02_branch_nor_gl_1k.jpg",
    "url": "https://dl.polyhaven.org/file/ph-assets/Models/jpg/1k/tree_small_02/tree_small_02_branch_nor_gl_1k.jpg",
    "md5": "00a3eed0d4d28e7e890d9ec6ac0619af",
    "license": "CC0",
    "asset": "tree_small_02",
    "source": "https://polyhaven.com/a/tree_small_02",
    "size": 581596
  },
  {
    "file": "assets/polyhaven/tree_small_02/textures/tree_small_02_branch_diff_1k.jpg",
    "url": "https://dl.polyhaven.org/file/ph-assets/Models/jpg/1k/tree_small_02/tree_small_02_branch_diff_1k.jpg",
    "md5": "88b531469f5dba2fc44ca85df3776e7c",
    "license": "CC0",
    "asset": "tree_small_02",
    "source": "https://polyhaven.com/a/tree_small_02",
    "size": 449977
  },
  {
    "file": "assets/polyhaven/tree_small_02/textures/tree_small_02_branch_arm_1k.jpg",
    "url": "https://dl.polyhaven.org/file/ph-assets/Models/jpg/1k/tree_small_02/tree_small_02_branch_arm_1k.jpg",
    "md5": "9ec3d8428295d87ae363bf0d1e0a8288",
    "license": "CC0",
    "asset": "tree_small_02",
    "source": "https://polyhaven.com/a/tree_small_02",
    "size": 375732
  },
  {
    "file": "assets/polyhaven/tree_small_02/textures/tree_small_02_leaves_nor_gl_1k.jpg",
    "url": "https://dl.polyhaven.org/file/ph-assets/Models/jpg/1k/tree_small_02/tree_small_02_leaves_nor_gl_1k.jpg",
    "md5": "b06c87e73f54d9654f66d67e9de7aa85",
    "license": "CC0",
    "asset": "tree_small_02",
    "source": "https://polyhaven.com/a/tree_small_02",
    "size": 681604
  },
  {
    "file": "assets/polyhaven/tree_small_02/textures/tree_small_02_leaves_diff_1k.jpg",
    "url": "https://dl.polyhaven.org/file/ph-assets/Models/jpg/1k/tree_small_02/tree_small_02_leaves_diff_1k.jpg",
    "md5": "315cbc2cebfb399e649ccd39112c109a",
    "license": "CC0",
    "asset": "tree_small_02",
    "source": "https://polyhaven.com/a/tree_small_02",
    "size": 340003
  },
  {
    "file": "assets/polyhaven/tree_small_02/textures/tree_small_02_leaves_arm_1k.jpg",
    "url": "https://dl.polyhaven.org/file/ph-assets/Models/jpg/1k/tree_small_02/tree_small_02_leaves_arm_1k.jpg",
    "md5": "3dad5bafc732f7692db32a262530bbab",
    "license": "CC0",
    "asset": "tree_small_02",
    "source": "https://polyhaven.com/a/tree_small_02",
    "size": 633698
  },
  {
    "file": "assets/polyhaven/tree_small_02/textures/tree_small_02_nor_gl_1k.jpg",
    "url": "https://dl.polyhaven.org/file/ph-assets/Models/jpg/1k/tree_small_02/tree_small_02_nor_gl_1k.jpg",
    "md5": "115a20da8621cd3e034e9b1bbe574746",
    "license": "CC0",
    "asset": "tree_small_02",
    "source": "https://polyhaven.com/a/tree_small_02",
    "size": 1228646
  },
  {
    "file": "assets/polyhaven/tree_small_02/textures/tree_small_02_diff_1k.jpg",
    "url": "https://dl.polyhaven.org/file/ph-assets/Models/jpg/1k/tree_small_02/tree_small_02_diff_1k.jpg",
    "md5": "a7287a3f4e7f0d4577cb949b245f5b14",
    "license": "CC0",
    "asset": "tree_small_02",
    "source": "https://polyhaven.com/a/tree_small_02",
    "size": 863246
  },
  {
    "file": "assets/polyhaven/tree_small_02/textures/tree_small_02_arm_1k.jpg",
    "url": "https://dl.polyhaven.org/file/ph-assets/Models/jpg/1k/tree_small_02/tree_small_02_arm_1k.jpg",
    "md5": "44b6ed4bf32d7cd67cb3e9d5faeacc38",
    "license": "CC0",
    "asset": "tree_small_02",
    "source": "https://polyhaven.com/a/tree_small_02",
    "size": 708242
  },
  {
    "file": "assets/polyhaven/tree_small_02/tree_small_02.bin",
    "url": "https://dl.polyhaven.org/file/ph-assets/Models/gltf/8k/tree_small_02/tree_small_02.bin",
    "md5": "7a7019a87e6fe0d7475c0fa2e08d8962",
    "license": "CC0",
    "asset": "tree_small_02",
    "source": "https://polyhaven.com/a/tree_small_02",
    "size": 95102324
  }
]
```

## 2026-10-21 新增细节资产

`assets/textures/detail-20261021/` 中细石、纸纤维、织物、釉面为本次原创程序材质；配方、随机种子和尺寸见同目录 source.json。铜钟贴图来自本模型程序材质的交换副本烘焙，不新增外部素材授权。

## 2026-10-21 生灵与景观

锦鲤外观图集及游动实现经用户授权从 Zenflow 只读复用，来源与哈希见 `assets/living/zenflow-koi/source.json`。锦鲤体积与红色花纹、两只简化猫为本次原创程序建模。低灌木复用项目已有 CC0 树叶卡片；未增加付费素材。
