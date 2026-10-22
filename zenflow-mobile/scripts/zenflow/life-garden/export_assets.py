"""Deterministic runtime adaptation of pinned GLB. Never opens or modifies .blend.
Requires the workstation's existing numpy and Pillow; no engine/dependency upgrades.
"""
from pathlib import Path
import copy, hashlib, io, json, struct
import numpy as np
from PIL import Image
ROOT = Path(__file__).resolve().parents[4]
SOURCE = ROOT / 'zenflow-space/docs/design/blender/zen-space/exports/zen-space-p3-v039.glb'
OUT = ROOT / 'zenflow-mobile/assets/zenflow/life-garden'
PIN = 'ac5519c038d8d5f72a34e72a1062514f6be64842ebad33b387f240cdbf73bfa7'

def glb(doc, data):
    j=json.dumps(doc,separators=(',',':'),ensure_ascii=True).encode();j+=b' '*((-len(j))%4);data+=b'\0'*((-len(data))%4)
    return struct.pack('<III',0x46546c67,2,28+len(j)+len(data))+struct.pack('<II',len(j),0x4e4f534a)+j+struct.pack('<II',len(data),0x004e4942)+data

def main():
    raw=SOURCE.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=PIN:raise ValueError('Pinned source hash mismatch')
    jlen=struct.unpack_from('<I',raw,12)[0];doc=json.loads(raw[20:20+jlen]);binary=raw[28+jlen:]
    OUT.mkdir(parents=True,exist_ok=True)
    files=[]
    def write(name,data):
        (OUT/name).write_bytes(data);files.append({'file':name,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
    # Public textures are shared, reduced to at most 512 px; alpha is retained.
    images=[]
    for image in doc['images']:
        v=doc['bufferViews'][image['bufferView']];start=v.get('byteOffset',0)
        im=Image.open(io.BytesIO(binary[start:start+v['byteLength']]));im.thumbnail((512,512),Image.Resampling.LANCZOS)
        buf=io.BytesIO();im.save(buf,format='WEBP',quality=85,method=6);data=buf.getvalue();name='texture-'+hashlib.sha256(data).hexdigest()[:20]+'.webp'
        if not any(f['file']==name for f in files):write(name,data)
        images.append({'name':image.get('name',''),'uri':name,'mimeType':'image/webp'})
    nodes=doc['nodes']; parents={c:i for i,n in enumerate(nodes)for c in n.get('children',[])};world={}
    def matrix(i):
        if i in world:return world[i]
        n=nodes[i]
        if 'matrix'in n:m=np.array(n['matrix']).reshape((4,4),order='F')
        else:
            x,y,z,w=n.get('rotation',[0,0,0,1]);m=np.eye(4)
            m[:3,:3]=np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])@np.diag(n.get('scale',[1,1,1]));m[:3,3]=n.get('translation',[0,0,0])
        world[i]=matrix(parents[i])@m if i in parents else m;return world[i]
    centers={'veranda':np.array([-13.4,-7.5]),'pond':np.array([-22,-3]),'tree':np.array([-26,-28.5])}
    groups={k:set()for k in ['common',*centers]}
    for i,n in enumerate(nodes):
        if 'mesh'not in n:continue
        name=n.get('name','');m=matrix(i);mesh=doc['meshes'][n['mesh']];pos=doc['accessors'][mesh['primitives'][0]['attributes']['POSITION']]
        center=(np.array(pos.get('min',[0,0,0]))+np.array(pos.get('max',[0,0,0])))/2
        point=(m@np.append(center,1))[[0,2]]
        distance={k:np.linalg.norm(point-v)for k,v in centers.items()};nearest=min(distance,key=distance.get)
        if 'SM_BACKGROUND_PINE' in name:
            if i%4==0:groups['common'].add(i)
        elif any(x in name for x in ['SM_CONTEXT_TREE','SM_CONTEXT_GROVE']):groups['common'].add(i)
        elif any(s in name.upper()for s in ['GRADED_GROUND','MOUNTAIN','TERRAIN','WATER_LOWER','POND_BOTTOM','OUTER_WALL']):groups['common'].add(i)
        elif distance[nearest]<=28:
            if any(z in name for z in ['Z04','Z05','Z06','Z11','Z12']) and any(detail in name for detail in ['TILES','PAVER','ROOF']):continue
            # Far decorative density is reduced, never interaction/camera anchors.
            if 'SM_BACKGROUND_PINE'in name and i%4!=0:continue
            groups[nearest].add(i)
    groups['overview']=set()
    for i,n in enumerate(nodes):
        name=n.get('name','').upper()
        if 'mesh' in n and i not in groups['common'] and any(k in name for k in ['ROOF','WALL','TERRACE','BRIDGE','COURT','WATER','ISLAND','GROUND']):
            if not any(k in name for k in ['TILES','PAVER','BRACKET','CUSHION','LAMP','DETAIL','JOINT']):groups['overview'].add(i)
    for group,mesh_nodes in groups.items():
        keep=set(mesh_nodes)|{i for i,n in enumerate(nodes)if 'camera'in n or n.get('name','').startswith('ANCHOR')}
        for i in list(keep):
            while i in parents:i=parents[i];keep.add(i)
        ids=sorted(keep);nm={old:new for new,old in enumerate(ids)}
        used_meshes=sorted({nodes[i]['mesh']for i in mesh_nodes});mm={old:new for new,old in enumerate(used_meshes)}
        meshes=[copy.deepcopy(doc['meshes'][i])for i in used_meshes]
        new_nodes=[]
        for i in ids:
            n=copy.deepcopy(nodes[i]);n['children']=[nm[x]for x in n.get('children',[])if x in keep]
            if i in mesh_nodes:n['mesh']=mm[n['mesh']]
            else:n.pop('mesh',None)
            new_nodes.append(n)
        animations=[]
        for a in doc.get('animations',[]):
            channels=[copy.deepcopy(c)for c in a['channels']if c['target'].get('node')in mesh_nodes or (c['target'].get('node')in keep and nodes[c['target']['node']].get('children'))]
            if not channels:continue
            samplers=sorted({c['sampler']for c in channels});sm={v:i for i,v in enumerate(samplers)}
            for c in channels:c['target']['node']=nm[c['target']['node']];c['sampler']=sm[c['sampler']]
            animations.append({'name':a.get('name',''),'channels':channels,'samplers':[copy.deepcopy(a['samplers'][s])for s in samplers]})
        access=set()
        for m in meshes:
            for p in m['primitives']:
                access.update(p['attributes'].values());access.add(p['indices'])
                for target in p.get('targets',[]):access.update(target.values())
        for a in animations:
            for s in a['samplers']:access.update([s['input'],s['output']])
        am={old:new for new,old in enumerate(sorted(access))};accessors=[copy.deepcopy(doc['accessors'][i])for i in sorted(access)]
        if any('sparse'in a for a in accessors):raise ValueError('Sparse accessor requires explicit adaptation')
        views=sorted({a['bufferView']for a in accessors});vm={old:new for new,old in enumerate(views)};payload=bytearray();new_views=[]
        for v in views:
            entry=copy.deepcopy(doc['bufferViews'][v]);start=entry.get('byteOffset',0);payload.extend(b'\0'*((-len(payload))%4));entry['byteOffset']=len(payload);payload.extend(binary[start:start+entry['byteLength']]);entry['buffer']=0;new_views.append(entry)
        for a in accessors:a['bufferView']=vm[a['bufferView']]
        for m in meshes:
            for p in m['primitives']:
                p['attributes']={k:am[v]for k,v in p['attributes'].items()};p['indices']=am[p['indices']]
                for target in p.get('targets',[]):
                    for k in target:target[k]=am[target[k]]
        for a in animations:
            for s in a['samplers']:s['input']=am[s['input']];s['output']=am[s['output']]
        result={k:copy.deepcopy(v)for k,v in doc.items()if k not in ['nodes','meshes','accessors','bufferViews','buffers','images','animations','scenes']}
        result.update(nodes=new_nodes,meshes=meshes,accessors=accessors,bufferViews=new_views,buffers=[{'byteLength':len(payload)}],images=images,animations=animations,scenes=[{'nodes':[nm[x]for x in doc['scenes'][0]['nodes']if x in keep]}])
        write(group+'.glb',glb(result,bytes(payload)))
        print(group,len(mesh_nodes),'meshes',round(files[-1]['bytes']/1024/1024,2),'MiB')
    # A textured binary probe is tested before loading real regions in WKWebView.
    probe={'asset':{'version':'2.0'},'scene':0,'scenes':[{'nodes':[0]}],'nodes':[{'mesh':0}], 'meshes':[{'primitives':[{'attributes':{'POSITION':0,'TEXCOORD_0':1},'indices':2,'material':0}]}], 'materials':[{'pbrMetallicRoughness':{'baseColorTexture':{'index':0},'metallicFactor':0}}],'textures':[{'source':0}], 'images':[{'uri':images[0]['uri']}], 'buffers':[{'byteLength':66}], 'bufferViews':[{'buffer':0,'byteOffset':0,'byteLength':36},{'buffer':0,'byteOffset':36,'byteLength':24},{'buffer':0,'byteOffset':60,'byteLength':6}], 'accessors':[{'bufferView':0,'componentType':5126,'count':3,'type':'VEC3','min':[-1,0,0],'max':[1,1,0]},{'bufferView':1,'componentType':5126,'count':3,'type':'VEC2'},{'bufferView':2,'componentType':5123,'count':3,'type':'SCALAR'}]}
    write('probe.glb',glb(probe,struct.pack('<15f3H',-1,0,0,1,0,0,0,1,0,0,0,1,0,.5,1,0,1,2)))
    mapping=json.loads((ROOT/'docs/design/ipad-v1-evidence/spatial-bindings-v1.json').read_text())
    views=[]
    labels=['廊下','鱼塘','坐垫','许愿树','书房入口','阅读席','抄写厅','抄写席','私人小屋','对话小屋','钟庭','老树通路','观景台','前门']
    en=['Veranda','Pond','Meditation','Wish tree','Library','Reading seat','Writing room','Writing seat','Private cabin','Conversation','Bell court','Old tree path','Lookout','Front gate']
    for i,v in enumerate(mapping['viewpoints']):
        views.append({'id':v['id'],'label':labels[i],'labelEn':en[i],'camera':v['referenceCameras'][0],'packageId':{'N00':'veranda','N01':'pond','N03':'tree'}.get(v['id']),'available':v['id']in ['N00','N01','N03'],'yawRange':[-.85,.85],'pitchRange':[-.3,.3],'fov':47,'near':.04,'far':550})
    manifest={'schemaVersion':1,'sceneVersion':'p3-v039-runtime-2','sourceSha256':PIN,'decoder':'uncompressed glTF 2 / Three.js vendored runtime','fallbackNodeId':'N00','overview':{'packageId':'overview','camera':'CAM_LAYOUT_TOP'},'files':sorted(files,key=lambda x:x['file']),'viewpoints':views,'environmentPresets':['day','dusk','night','rain','dawn','sunset'],'status':'DEVELOPMENT_ASSET_NOT_RELEASE_ACCEPTED','adaptation':{'maxTextureSize':512,'nearRadius':28,'deferredDistantDetail':'Z04/Z05/Z06/Z11/Z12 roofs, tiles and pavers; visual acceptance pending','backgroundPineStride':4,'missingVisualEntities':'See spatial-bindings-v1.json; native buttons remain explicit runtime proxies'}}
    (OUT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print('Total public runtime assets MiB',round(sum(f['bytes']for f in files)/1024/1024,2))
if __name__=='__main__':main()
