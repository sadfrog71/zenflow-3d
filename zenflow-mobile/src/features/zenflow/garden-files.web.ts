import { Asset } from 'expo-asset';
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

export async function prepareGarden(): Promise<{ uri: string; directory: string; html: string }> {
  // Opaque iframe origins cannot fetch WebGL textures without CORS. Embed bundled
  // textures, as the native renderer already does, without granting iframe origin access.
  const textures = Object.fromEntries(await Promise.all(images.map(async ([name, module]) => {
    const response = await fetch(new URL(Asset.fromModule(module).uri, window.location.href));
    if (!response.ok) throw new Error('GARDEN_ASSET_UNAVAILABLE');
    const blob = await response.blob();
    const data = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader(); reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => reject(new Error('GARDEN_ASSET_UNAVAILABLE')); reader.readAsDataURL(blob);
    });
    return [name, data];
  })));
  const html = source.html.replace('<script>', `<script>window.InvelleGardenAssets=${JSON.stringify(textures)};`);
  return { uri: '', directory: '', html };
}
