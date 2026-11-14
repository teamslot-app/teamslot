import { useLoader } from '@react-three/fiber'
import { useEffect, useMemo, useRef } from 'react'
import {
  DoubleSide,
  EquirectangularReflectionMapping,
  Group,
  RepeatWrapping,
  SRGBColorSpace,
  TextureLoader,
  type Scene,
  type Texture,
} from 'three'
import { HDRLoader } from 'three/examples/jsm/loaders/HDRLoader.js'
import { jeton } from './jetons'
import { textureGrillage, textureMarquage, type Stade } from './stade'

const MARGE_GRILLAGE = 1.2
const HAUTEUR_GRILLAGE = 4.4
const HAUTEUR_BRISE_VUE = 4 // toile verte tendue sur le grillage, comme sur les terrains de quartier
const TAILLE_SOL = 400 // la pelouse va jusqu'à l'horizon
const TAILLE_MOTIF = 3 // une photo d'herbe couvre 3 × 3 unités

/**
 * Lumière et ciel réels : l'image HDRI (photo 360° d'un terrain de quartier au coucher du soleil) sert à la
 * fois de fond et d'éclairage (reflets du ballon, ambiance). Un soleil rasant ajoute les ombres portées.
 */
export function Ciel() {
  const charge = useLoader(HDRLoader, '/accueil/terrain.hdr')
  const ancre = useRef<Group>(null)
  const hdri = useMemo(() => {
    const t = charge.clone()
    t.mapping = EquirectangularReflectionMapping
    return t
  }, [charge])
  useEffect(() => {
    // la scène est le parent de l'ancre : on y branche le ciel (fond) et l'éclairage d'environnement
    const scene = ancre.current?.parent as Scene | null
    if (!scene) return
    scene.background = hdri
    scene.environment = hdri
    scene.backgroundRotation.set(0, Math.PI * 0.5, 0)
    scene.environmentRotation.set(0, Math.PI * 0.5, 0)
    return () => {
      scene.background = null
      scene.environment = null
    }
  }, [hdri])
  return (
    <group ref={ancre}>
      <directionalLight
        position={[-9, 7, -6]}
        intensity={1.6}
        castShadow
        shadow-mapSize-width={2048}
        shadow-mapSize-height={2048}
        shadow-camera-left={-14}
        shadow-camera-right={14}
        shadow-camera-top={14}
        shadow-camera-bottom={-14}
        shadow-bias={-0.0005}
      />
    </group>
  )
}

/** La pelouse : photo d'herbe répétée jusqu'à l'horizon (avec son relief), et par-dessus le marquage du terrain. */
export function Pelouse({ stade, largeurCage }: { stade: Stade; largeurCage: number }) {
  const [photo, photoRelief] = useLoader(TextureLoader, ['/accueil/herbe.jpg', '/accueil/herbe-normal.jpg'])
  const marquage = useMemo(() => textureMarquage(stade, largeurCage), [stade, largeurCage])
  const [herbe, relief] = useMemo(() => {
    const repeter = (t: Texture) => {
      const copie = t.clone()
      copie.wrapS = copie.wrapT = RepeatWrapping
      copie.repeat.set(TAILLE_SOL / TAILLE_MOTIF, TAILLE_SOL / TAILLE_MOTIF)
      copie.anisotropy = 8
      return copie
    }
    const h = repeter(photo)
    h.colorSpace = SRGBColorSpace
    return [h, repeter(photoRelief)]
  }, [photo, photoRelief])
  const longueur = stade.zPres - stade.zBut
  return (
    <>
      <mesh rotation-x={-Math.PI / 2} position={[stade.cx, 0, 0]} receiveShadow>
        <planeGeometry args={[TAILLE_SOL, TAILLE_SOL]} />
        <meshStandardMaterial map={herbe} normalMap={relief} roughness={1} />
      </mesh>
      <mesh rotation-x={-Math.PI / 2} position={[stade.cx, 0.004, (stade.zPres + stade.zBut) / 2]} receiveShadow>
        <planeGeometry args={[stade.demiLargeur * 2, longueur]} />
        <meshStandardMaterial map={marquage} transparent roughness={1} depthWrite={false} />
      </mesh>
    </>
  )
}

/**
 * Le grillage du terrain de quartier, avec sa toile brise-vue verte : au fond et sur les côtés (pas devant,
 * il cacherait le jeu). La toile cache aussi la ligne d'horizon de la photo HDRI.
 * Sur ordinateur, pas de côté gauche : il passerait derrière le texte.
 */
export function Grillage({ stade, coteGauche }: { stade: Stade; coteGauche: boolean }) {
  const maille = useMemo(() => textureGrillage(), [])
  const metal = useMemo(() => jeton('metal'), [])
  const toile = useMemo(() => jeton('horizon'), [])
  const x0 = stade.cx - stade.demiLargeur - MARGE_GRILLAGE
  const x1 = stade.cx + stade.demiLargeur + MARGE_GRILLAGE
  const zFond = stade.zBut - MARGE_GRILLAGE - 1.2
  const zAvant = stade.zPres + 4
  const panneaux = [
    {
      centre: [(x0 + x1) / 2, HAUTEUR_GRILLAGE / 2, zFond],
      rotY: 0,
      longueur: x1 - x0,
    },
    ...(coteGauche
      ? [
          {
            centre: [x0, HAUTEUR_GRILLAGE / 2, (zFond + zAvant) / 2],
            rotY: Math.PI / 2,
            longueur: zAvant - zFond,
          },
        ]
      : []),
    {
      centre: [x1, HAUTEUR_GRILLAGE / 2, (zFond + zAvant) / 2],
      rotY: Math.PI / 2,
      longueur: zAvant - zFond,
    },
  ]
  const poteaux = useMemo(() => {
    const liste: [number, number][] = []
    for (let x = x0; x <= x1 + 0.01; x += 3) liste.push([x, zFond])
    for (let z = zFond + 3; z <= zAvant; z += 3)
      liste.push(...(coteGauche ? [[x0, z] as [number, number]] : []), [x1, z])
    return liste
  }, [x0, x1, zFond, zAvant, coteGauche])
  return (
    <group>
      {panneaux.map(({ centre, rotY, longueur }, i) => {
        const texture = maille.clone()
        texture.repeat.set(longueur / 0.5, HAUTEUR_GRILLAGE / 0.5)
        texture.needsUpdate = true
        return (
          <mesh key={i} position={centre as unknown as [number, number, number]} rotation-y={rotY}>
            <planeGeometry args={[longueur, HAUTEUR_GRILLAGE]} />
            <meshStandardMaterial
              map={texture}
              transparent
              alphaTest={0.3}
              side={DoubleSide}
              color={metal}
              metalness={0.6}
              roughness={0.4}
            />
          </mesh>
        )
      })}
      {panneaux.map(({ centre, rotY, longueur }, i) => (
        <mesh
          key={`toile-${i}`}
          position={[
            centre[0] + (rotY ? 0.05 * Math.sign(centre[0] - stade.cx) : 0),
            HAUTEUR_BRISE_VUE / 2,
            centre[2] - (rotY ? 0 : 0.05),
          ]}
          rotation-y={rotY}
          receiveShadow
        >
          <planeGeometry args={[longueur, HAUTEUR_BRISE_VUE]} />
          <meshStandardMaterial color={toile} roughness={0.95} side={DoubleSide} />
        </mesh>
      ))}
      {poteaux.map(([x, z]) => (
        <mesh key={`${x}:${z}`} position={[x, HAUTEUR_GRILLAGE / 2, z]} castShadow>
          <cylinderGeometry args={[0.04, 0.04, HAUTEUR_GRILLAGE, 8]} />
          <meshStandardMaterial color={metal} metalness={0.7} roughness={0.35} />
        </mesh>
      ))}
    </group>
  )
}
