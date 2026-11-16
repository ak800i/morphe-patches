# ak800i patches

Morphe patches for various apps. Each app has its own section below.

| App | Patches |
| --- | --- |
| [Waze](#waze) | [Voice pack import and export](#voice-pack-import-and-export) |

## How to use these patches

Click the button below, or add this repo URL to Morphe Manager by hand: https://github.com/ak800i/morphe-patches

<a href="https://morphe.software/add-source?github=ak800i/morphe-patches">
  <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48">
</a>

## Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.0.1](https://github.com/ak800i/morphe-patches/releases/tag/v1.0.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;1 patches total
<details open>
<summary>📦 Waze&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.waze`

**🎯 Supported versions:**

| 5.27.5.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Voice pack import and export](#voice-pack-import-and-export) | Adds Import and Export buttons to the custom voice screen, so custom voices can be shared as zip files of MP3 prompts. |  |

</details>

<!-- PATCHES_END -->

## Waze

Patches for Waze (`com.waze`). You can apply them together with
[bearinmind patches](https://github.com/bearinmindcat/morphe-patches) to the same Waze. Tested with
bearinmind patches 2.10.0 and all of its Waze patches that are on by default.

### Voice pack import and export

Waze lets you record your own voice for navigation prompts (Settings > Voice & sound > Voice >
Record a new voice). This patch adds two buttons at the top of that recording screen:

- **Import pack** opens a zip file and puts its prompts on the screen. This replaces any
  prompts that are already on the screen. Then tap **Done** and save the voice as usual. The
  name field is filled in from the pack. If you import while editing an existing voice, the
  voice keeps its current name.
- **Export pack** saves the prompts on the screen as a zip file. To export a voice that you
  already have, open it from the voice list (edit or view it), then tap **Export pack**.
  Import is not shown for voices that you can only view.

Waze still saves, uploads and shares the voice itself. The patch only fills or reads the
prompts on the recording screen.

#### Zip structure

```
My voice.zip
├── voicepack.json     optional metadata
├── StartDrive1.mp3    one file per prompt, named <promptId>.mp3
├── TurnLeft.mp3
├── TurnRight.mp3
└── ...
```

Import is lenient:

- Prompt files can be at the top of the zip or in any folder inside it.
- Prompt ids are matched without regard to case, so `turnleft.mp3` works.
- Files whose names are not Waze prompt ids, and files that are not `.mp3`, are skipped.
  The patch shows how many files it skipped.
- You don't need every prompt. Waze only requires at least one recording. Prompts without
  a recording use the fallback voice that you choose in Waze.
- Each prompt can be at most 8 MB.

The audio can be any format that Android's media player can play: MP3, or AAC/M4A. Waze's own
recordings are AAC audio in files that end in `.mp3`, so exported packs contain those files
unchanged. Keep each prompt short, because Waze plays it in the middle of navigation. The
recording screen shows the maximum length for each prompt.

#### Prompt ids

The patch reads the list of prompt ids from Waze when it runs, so it always matches the
installed Waze version. The safest way to get the exact file names is to record one prompt,
export the voice, and look at the zip.

Waze 5.27.5.0 has these 43 prompt ids:

| Category | Prompt ids |
| --- | --- |
| Start of a drive | `StartDrive1` … `StartDrive9` |
| Distances (imperial) | `200`, `400`, `800`, `1500` (a tenth, a quarter, half a mile, one mile) |
| Distances (metric) | `200meters`, `400meters`, `800meters`, `1000meters`, `1500meters` |
| Instructions | `KeepLeft`, `KeepRight`, `TurnLeft`, `TurnRight`, `ExitLeft`, `ExitRight`, `Straight`, `uturn`, `Roundabout`, `AndThen`, `Arrive` |
| Roundabout exits | `First`, `Second`, `Third`, `Fourth`, `Fifth`, `Sixth`, `Seventh` |
| Alerts | `Police`, `ApproachAccident`, `ApproachHazard`, `ApproachTraffic`, `ApproachRedLightCam`, `ApproachSpeedCam` |
| Other | `TickerPoints` |

Waze shows only the metric or the imperial distances, depending on your units setting. If a
later Waze version changes the list, export a voice to see the new names, or run
`adb logcat -s WazeVoicePack` while you tap Import or Export; the patch logs the full list.

#### voicepack.json

The metadata file is optional. Export always writes it.

```json
{
  "format": "waze-voicepack",
  "version": 1,
  "name": "My voice",
  "createdAt": "2026-11-16T12:43:30Z",
  "app": { "package": "com.waze", "version": "5.27.5.0" },
  "prompts": ["StartDrive1", "TurnLeft", "TurnRight"]
}
```

| Field | Meaning |
| --- | --- |
| `format` | Always `waze-voicepack`. If it is present with another value, the import is refused. |
| `version` | Version of this format, currently `1`. A newer version is still imported, with a warning. |
| `name` | Name of the voice. Import uses it to fill in the name field (Waze allows 40 characters). Without it, the zip's file name is used. |
| `createdAt` | When the pack was exported, in UTC. Informational. |
| `app` | The Waze package and version that exported the pack. Informational. |
| `prompts` | The prompt ids in the pack. Informational; import uses the `.mp3` files that are actually in the zip. |

## Development

- Build with `./gradlew build`. The patch bundle is written to `patches/build/libs/patches-*.mpp`.
  The build needs a GitHub token that can read packages, as described in the
  [Morphe patcher setup guide](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/2_1_setup.md#-prepare-the-environment).
- Apply the bundle with [Morphe Desktop](https://github.com/MorpheApp/morphe-desktop):
  `java -jar morphe-desktop-all.jar patch -p patches/build/libs/patches-1.0.0.mpp waze.apkm`
- The patch code is in [patches/src/main/kotlin/app/ak800i/patches/waze](patches/src/main/kotlin/app/ak800i/patches/waze).
  The code that runs inside Waze is in
  [extensions/waze/src/main/java/app/ak800i/extension/waze](extensions/waze/src/main/java/app/ak800i/extension/waze).
- Use [semantic commit](https://kapeli.com/cheat_sheets/Semantic_Commits.docset/Contents/Resources/Documents/index)
  messages (`feat:`, `fix:`, `chore:`); the release workflow builds the changelog from them.

## License

GPLv3. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
