import type {Citation} from '@/core/zenflow/core';
export type AnswerSegment={kind:'text';text:string}|{kind:'quote';text:string;source:Citation};
export function answerSegments(content:string,sources:Citation[]):AnswerSegment[]{
 const result:AnswerSegment[]=[];const pattern=/(?:^|\n)((?:>[^\n]*(?:\n|$))+)[ \t]*(\[[A-Z0-9_]+\])?/g;let end=0;
 for(const match of content.matchAll(pattern)){
  const block=match[1].split('\n').filter(line=>line.startsWith('>')).map(line=>line.replace(/^>\s?/, '')).join('\n').trim();
  const inline=block.match(/\s*\[([A-Z0-9_]+)\]$/);
  const quote=inline?block.slice(0,inline.index).trim():block;
  const marker=match[2]?.slice(1,-1)||inline?.[1];const source=sources.find(source=>source.visibility==='PUBLIC'&&source.excerpt.includes(quote)&&(!marker||source.citationKey===marker));
  if(!source)continue;
  const offset=match.index??0;if(offset>end)result.push({kind:'text',text:content.slice(end,offset)});result.push({kind:'quote',text:quote,source});end=offset+match[0].length;
 }
 if(end<content.length)result.push({kind:'text',text:content.slice(end)});return result;
}
