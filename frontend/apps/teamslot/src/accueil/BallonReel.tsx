import { useLoader } from '@react-three/fiber'
import { useMemo } from 'react'
import { Box3, Mesh, Vector3 } from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'

/**
 * Le vrai ballon : modèle 3D « Dirty Football » de Poly Haven (CC0), avec ses textures photographiées
 * (couleur, relief, rugosité). Fichiers séparés (.gltf + .bin + .jpg) et non .glb : un .glb crée des URL
 * « blob: » pour ses images, que la CSP de production bloque.
 * Le modèle est recentré et mis à l'échelle pour faire exactement le diamètre demandé.
 */
export default function BallonReel({ rayon }: { rayon: number }) {
  const gltf = useLoader(GLTFLoader, '/accueil/ballon/ballon.gltf')
  const objet = useMemo(() => {
    const copie = gltf.scene.clone(true)
    copie.updateMatrixWorld(true) // sinon la boîte englobante ignore la translation du modèle (ballon qui flotte)
    const boite = new Box3().setFromObject(copie)
    const centre = boite.getCenter(new Vector3())
    const taille = boite.getSize(new Vector3())
    const echelle = (rayon * 2) / Math.max(taille.x, taille.y, taille.z)
    copie.position.sub(centre).multiplyScalar(echelle)
    copie.scale.multiplyScalar(echelle)
    copie.traverse((o) => {
      if (o instanceof Mesh) o.castShadow = true
    })
    return copie
  }, [gltf, rayon])
  return <primitive object={objet} />
}
