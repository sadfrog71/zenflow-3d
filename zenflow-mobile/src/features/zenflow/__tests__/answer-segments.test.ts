import {answerSegments} from '../answer-segments';
const source={citationKey:'REFERENCE_123',title:'A source',excerpt:'Remain here. Let attention rest.',visibility:'PUBLIC' as const,runId:'r',sourceType:'REFERENCE',revisionId:'1'};
test('only an exact available source passage becomes a source block',()=>{
 const sections=answerSegments('A thought.\n\n> Remain here.\n[REFERENCE_123]\n\nMy explanation.',[source]);
 expect(sections.find(section=>section.kind==='quote')).toMatchObject({text:'Remain here.',source});
 expect(sections.map(section=>section.text).join(' ')).toContain('My explanation.');
 expect(answerSegments('> Invented quote.\n[REFERENCE_123]',[source]).every(section=>section.kind==='text')).toBe(true);
 expect(answerSegments('> Remain here.\n[REFERENCE_123]',[]).every(section=>section.kind==='text')).toBe(true);
});

test('real supplier inline source marker is excluded from the verbatim quote',()=>{
 expect(answerSegments('> Remain here.[REFERENCE_123]\n\nAn explanation.',[source])[0]).toMatchObject({kind:'quote',text:'Remain here.',source});
 expect(answerSegments('> Remain here.[REFERENCE_OTHER]',[source]).every(part=>part.kind==='text')).toBe(true);
});
