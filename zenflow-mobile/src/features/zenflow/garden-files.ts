import { Asset } from 'expo-asset';
import * as FileSystem from 'expo-file-system/legacy';
import source from './garden-source.json';

const images: [string, number][] = [
  ['cinematic-lake-20261015.png', require('../../../assets/zenflow/garden/assets/cinematic-lake-20261015.png')],
  ['deck-color.jpg', require('../../../assets/zenflow/garden/assets/deck-color.jpg')],
  ['deck-normal.jpg', require('../../../assets/zenflow/garden/assets/deck-normal.jpg')],
  ['deck-roughness.jpg', require('../../../assets/zenflow/garden/assets/deck-roughness.jpg')],
  ['moss-color.jpg', require('../../../assets/zenflow/garden/assets/moss-color.jpg')],
  ['moss-normal.jpg', require('../../../assets/zenflow/garden/assets/moss-normal.jpg')],
  ['moss-roughness.jpg', require('../../../assets/zenflow/garden/assets/moss-roughness.jpg')],

  ['mountain-lake.png', require('../../../assets/zenflow/garden/assets/mountain-lake.png')],
  ['cushion-top.png', require('../../../assets/zenflow/garden/assets/cushion-top.png')],
  ['white_plaster_rough_01-normal.jpg', require('../../../assets/zenflow/garden/assets/white_plaster_rough_01-normal.jpg')],
  ['bark_brown_01-color.jpg', require('../../../assets/zenflow/garden/assets/bark_brown_01-color.jpg')],
  ['bark_brown_01-normal.jpg', require('../../../assets/zenflow/garden/assets/bark_brown_01-normal.jpg')],
];

export async function prepareGarden(): Promise<{ uri: string; directory: string }> {
  const directory = `${FileSystem.documentDirectory}invelle-garden-${source.revision}/`;
  if (!FileSystem.documentDirectory) throw new Error('GARDEN_FILES_UNAVAILABLE');
  const html = `${directory}index.html`;
  if (!(await FileSystem.getInfoAsync(html)).exists) {
    await FileSystem.makeDirectoryAsync(`${directory}assets/`, { intermediates: true });
    const textures: Record<string, string> = {};
    for (const [name, module] of images) {
      const asset = Asset.fromModule(module);
      await asset.downloadAsync();
      if (!asset.localUri) throw new Error('GARDEN_ASSET_UNAVAILABLE');
      await FileSystem.copyAsync({ from: asset.localUri, to: `${directory}assets/${name}` });
      textures[name] = `data:image/${name.endsWith('.png') ? 'png' : 'jpeg'};base64,${await FileSystem.readAsStringAsync(asset.localUri, { encoding: FileSystem.EncodingType.Base64 })}`;
    }
    await FileSystem.writeAsStringAsync(`${directory}scene.html`, source.sceneHtml);
    // Write the entry last so an interrupted copy is retried on next launch.
    // Local file origins cannot supply cross-origin WebGL textures on WKWebView.
    // Data URLs keep the scene offline without expanding file/network permissions.
    await FileSystem.writeAsStringAsync(html, source.html.replace('<script>', `<script>window.InvelleGardenAssets=${JSON.stringify(textures)};`));
  }
  return { uri: html, directory };
}
