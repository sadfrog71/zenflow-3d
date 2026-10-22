import Svg, { Circle, Path } from 'react-native-svg';

export type GardenTab = 'rest' | 'chat' | 'create' | 'notes';

/** Quiet line drawings share one grid, weight and rounded stroke. */
export function TabIcon({ tab, selected }: { tab: GardenTab; selected: boolean }) {
  const color = selected ? '#2f513d' : '#7b877d';
  return <Svg width={26} height={26} viewBox="0 0 24 24" fill="none"
    stroke={color} strokeWidth={1.55} strokeLinecap="round" strokeLinejoin="round"
    accessible={false}>
    {tab === 'rest' && <>
      <Circle cx={12} cy={4.2} r={1.8} />
      <Path d="M8.6 10.2c.8-1.5 1.9-2.2 3.4-2.2s2.6.7 3.4 2.2l1.9 3.5 2.1.7M8.6 10.2l-1.9 3.5-2.1.7M10 10.8v3.4m4-3.4v3.4M12 16.8c-2-2-4.9-2.7-7-1.2-1.6 1.1-1 3.8 1.4 4.2h11.2c2.4-.4 3-3.1 1.4-4.2-2.1-1.5-5-.8-7 1.2Zm0 0 3.6 1.5M12 16.8l-3.6 1.5" />
    </>}
    {tab === 'chat' && <>
      <Path d="M19.5 11.2c0 4-3.4 7.2-7.5 7.2-1.1 0-2.2-.2-3.2-.7l-4.3 1.5 1.2-4.1a7 7 0 0 1-1.2-3.9C4.5 7.2 7.9 4 12 4s7.5 3.2 7.5 7.2Z" />
      <Path d="M8.2 10.6h7.6M8.2 13.5h4.7" />
    </>}
    {tab === 'create' && <>
      <Path d="m10 15 7.8-9.2c.7-.9 2-.9 2.7-.2s.7 2 0 2.7L11.3 16M16.4 7.4l2.3 2.3M10 15c-2.6-.6-4.2.8-4.2 3 0 1-.6 1.6-1.5 2.1 4.6.8 7.9-1 7-4.1" />
      <Path d="M5.2 4v3.8M3.3 5.9h3.8" />
    </>}
    {tab === 'notes' && <>
      <Path d="M7 3.8h9.2l3 3v13.4H7a2.2 2.2 0 0 1-2.2-2.2V6A2.2 2.2 0 0 1 7 3.8ZM16.2 3.8v3h3M8.5 10.3h7M8.5 13.8h7M8.5 17.3h4.4" />
    </>}
  </Svg>;
}
