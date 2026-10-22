// Local low-poly koi; reuse the courtyard renderer and its motion/lifecycle loop.
const invelleKoi=[];
function createInvelleKoi(){
 const colors=[['#ead9b3','#cc6145'],['#eee4cf','#ba3e30'],['#d8d3bd','#455848']];
 colors.forEach(([base,patch],i)=>{
  const group=new THREE.Group();group.scale.setScalar(.43);scene.add(group);
  const material=new THREE.MeshStandardMaterial({color:base,roughness:.65,transparent:true,opacity:.55});
  const body=new THREE.Mesh(new THREE.SphereGeometry(1,16,10),material);body.scale.set(.13,.05,.34);group.add(body);
  for(const [x,z,r] of [[-.025,-.14,.07],[.035,.1,.085]]){const mark=new THREE.Mesh(new THREE.SphereGeometry(1,12,6),new THREE.MeshStandardMaterial({color:patch,roughness:.72}));mark.position.set(x,.043,z);mark.scale.set(r,.012,r*1.5);group.add(mark)}
  const finMaterial=new THREE.MeshStandardMaterial({color:patch,side:THREE.DoubleSide,transparent:true,opacity:.67,roughness:.8});
  const shape=new THREE.Shape();shape.moveTo(0,0);shape.lineTo(-.12,.22);shape.quadraticCurveTo(0,.15,.12,.22);shape.closePath();
  const tail=new THREE.Mesh(new THREE.ShapeGeometry(shape),finMaterial);tail.rotation.x=Math.PI/2;tail.position.z=.28;group.add(tail);
  for(const side of [-1,1]){const fin=new THREE.Mesh(new THREE.SphereGeometry(1,10,5),finMaterial);fin.scale.set(.13,.007,.08);fin.position.set(side*.14,0,-.03);group.add(fin);const eye=new THREE.Mesh(new THREE.SphereGeometry(.014,8,6),new THREE.MeshBasicMaterial({color:'#293b32'}));eye.position.set(side*.08,.035,-.24);group.add(eye)}
  invelleKoi.push({group,tail,phase:i*Math.PI*2/3,speed:.12+i*.015});
 });updateInvelleKoi(0);
}
function updateInvelleKoi(time){for(const fish of invelleKoi){const a=time*fish.speed+fish.phase;fish.group.position.set(1.0*Math.cos(a),-.208,-3.6+1.0*Math.sin(a));fish.group.rotation.y=Math.atan2(Math.sin(a),-Math.cos(a));fish.tail.rotation.y=Math.sin(time*2.8+fish.phase)*.24;fish.group.rotation.z=Math.sin(time*.7+fish.phase)*.015}}
