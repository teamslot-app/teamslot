import { Canvas, useFrame, useThree, type ThreeEvent } from '@react-three/fiber'
import { useMemo, useRef } from 'react'
import { Group, Plane, Quaternion, Vector3 } from 'three'
import BallonReel from './BallonReel'
import { Ciel, Grillage, Pelouse } from './Decor'
import { jeton } from './jetons'
import type { Stade } from './stade'

const RAYON = 0.45
const GRAVITE = -24
const REBOND = 0.55
const FROTTEMENT = 0.25 // perte de vitesse par seconde quand le ballon roule
const ELAN = 1.8 // un tir part un peu plus vite que le geste : la cage doit être atteignable d'un petit geste
const VITESSE_MAX = 18
const LIMITES = { zPres: 4.5 }
const BORD_ECRAN = 0.9 // le ballon rebondit avant de sortir de l'image (coordonnée écran, 1 = bord)

/** La cage : ligne de but à z, ouverture de largeur × hauteur, filet profond de profondeur. */
export type Cage = { x: number; z: number; largeur: number; hauteur: number; profondeur: number }

/** Poteaux, barre transversale et filet (un quadrillage de fils), à partir de la position de la cage. */
function CageDeBut({ cage }: { cage: Cage }) {
  const craie = useMemo(() => jeton('craie'), [])
  const { largeur: l, hauteur: h, profondeur: p } = cage
  const filet = useMemo(() => {
    const fils: [number, number, number, number, number, number][] = []
    for (let x = -l / 2; x <= l / 2 + 0.001; x += 0.3) fils.push([x, h, -0.02, x, 0, -p]) // fils qui tombent vers le fond
    for (let y = 0; y <= h + 0.001; y += 0.3) fils.push([-l / 2, y * 1, -p * (1 - y / h) - 0.02, l / 2, y, -p * (1 - y / h) - 0.02])
    for (let z = 0.3; z <= p + 0.001; z += 0.3) {
      fils.push([-l / 2, h * (1 - z / p), -z, -l / 2, 0, -z]) // côtés
      fils.push([l / 2, h * (1 - z / p), -z, l / 2, 0, -z])
    }
    return new Float32Array(fils.flat())
  }, [l, h, p])
  const poteau = 0.07
  return (
    <group position={[cage.x, 0, cage.z]}>
      <mesh position={[-l / 2, h / 2, 0]} castShadow>
        <cylinderGeometry args={[poteau, poteau, h, 12]} />
        <meshStandardMaterial color={craie} roughness={0.4} />
      </mesh>
      <mesh position={[l / 2, h / 2, 0]} castShadow>
        <cylinderGeometry args={[poteau, poteau, h, 12]} />
        <meshStandardMaterial color={craie} roughness={0.4} />
      </mesh>
      <mesh position={[0, h, 0]} rotation-z={Math.PI / 2} castShadow>
        <cylinderGeometry args={[poteau, poteau, l + poteau * 2, 12]} />
        <meshStandardMaterial color={craie} roughness={0.4} />
      </mesh>
      <lineSegments>
        <bufferGeometry>
          <bufferAttribute attach="attributes-position" args={[filet, 3]} />
        </bufferGeometry>
        <lineBasicMaterial color={craie} transparent opacity={0.35} />
      </lineSegments>
    </group>
  )
}

/**
 * Le ballon posé sur le point de coup d'envoi, avec une petite physique maison (pas de moteur physique :
 * trop lourd pour un accueil). On l'attrape, on le traîne sur la pelouse, on le lâche : il part, rebondit,
 * roule et ralentit. Au repos, il sautille de temps en temps pour inviter à jouer.
 */
function Ballon({ depart, cage, stade, onBut }: { depart: Vector3; cage: Cage; stade: Stade; onBut: () => void }) {
  const groupe = useRef<Group>(null)
  const etat = useRef({
    position: depart.clone(), // posé sur le point de coup d'envoi
    vitesse: new Vector3(),
    tenu: false,
    derniers: [] as { p: Vector3; t: number }[],
    repos: 0,
    dansLeFilet: false,
    remiseDans: 0,
  })
  const solDeJeu = useMemo(() => new Plane(new Vector3(0, 1, 0), -RAYON), [])
  const point = useMemo(() => new Vector3(), [])
  const axe = useMemo(() => new Vector3(), [])
  const ecran = useMemo(() => new Vector3(), [])
  const { camera } = useThree()
  const rotation = useMemo(() => new Quaternion(), [])

  useFrame((_, dtBrut) => {
    const dt = Math.min(dtBrut, 1 / 30)
    const e = etat.current
    const g = groupe.current
    if (!g) return

    if (!e.tenu) {
      e.vitesse.y += GRAVITE * dt
      e.position.addScaledVector(e.vitesse, dt)
      if (e.position.y <= RAYON) {
        e.position.y = RAYON
        e.vitesse.y = Math.abs(e.vitesse.y) < 1 ? 0 : -e.vitesse.y * REBOND
        const ralenti = Math.max(0, 1 - FROTTEMENT * dt)
        e.vitesse.x *= ralenti
        e.vitesse.z *= ralenti
      }
      // Grillage sur les côtés : le ballon rebondit dessus
      const cote = stade.demiLargeur + 0.9
      if (Math.abs(e.position.x - stade.cx) > cote) {
        e.position.x = stade.cx + Math.sign(e.position.x - stade.cx) * cote
        e.vitesse.x *= -REBOND
      }
      // Bords gauche/droit : on projette le ballon à l'écran ; s'il atteint le bord, il rebondit vers l'intérieur
      ecran.copy(e.position).project(camera)
      if (Math.abs(ecran.x) > BORD_ECRAN) {
        e.position.x *= BORD_ECRAN / Math.abs(ecran.x)
        e.vitesse.x = -Math.sign(e.position.x) * Math.abs(e.vitesse.x) * REBOND
      }
      if (e.position.z > LIMITES.zPres) {
        e.position.z = LIMITES.zPres
        e.vitesse.z *= -REBOND
      }
      // La ligne de but : dans l'ouverture → BUT, le filet freine le ballon ; à côté → il rebondit sur le fond
      const dansLaLargeur = Math.abs(e.position.x - cage.x) < cage.largeur / 2 - RAYON * 0.6
      const sousLaBarre = e.position.y < cage.hauteur - RAYON * 0.4
      if (!e.dansLeFilet && e.position.z - RAYON * 0.2 < cage.z) {
        if (dansLaLargeur && sousLaBarre) {
          e.dansLeFilet = true
          e.remiseDans = 2.2
          onBut()
        } else if (e.position.z < cage.z - 0.6) {
          e.position.z = cage.z - 0.6
          e.vitesse.z = Math.abs(e.vitesse.z) * REBOND
        }
      }
      if (e.dansLeFilet) {
        const fond = cage.z - cage.profondeur + RAYON
        if (e.position.z < fond) {
          e.position.z = fond
          e.vitesse.z = Math.abs(e.vitesse.z) * 0.15
          e.vitesse.x *= 0.3
        }
        e.position.x = Math.max(cage.x - cage.largeur / 2 + RAYON, Math.min(cage.x + cage.largeur / 2 - RAYON, e.position.x))
        e.position.y = Math.min(e.position.y, cage.hauteur - RAYON)
        e.remiseDans -= dt
        if (e.remiseDans <= 0) {
          // remise en jeu : le ballon revient au point de coup d'envoi
          e.dansLeFilet = false
          e.position.copy(depart).setY(RAYON + 2)
          e.vitesse.set(0, 0, 0)
        }
      }
      const immobile = e.position.y <= RAYON + 0.001 && e.vitesse.lengthSq() < 0.01
      e.repos = immobile ? e.repos + dt : 0
      if (e.repos > 3.5 && !e.dansLeFilet) {
        e.vitesse.set(0, 5.5, 0) // petit saut d'invitation
        e.repos = 0
      }
    }

    g.position.copy(e.position)
    const vSol = Math.hypot(e.vitesse.x, e.vitesse.z)
    if (vSol > 0.01) {
      // Roulement : avancer de d fait tourner de d / rayon autour de l'axe (haut × vitesse)
      axe.set(e.vitesse.z, 0, -e.vitesse.x).normalize()
      rotation.setFromAxisAngle(axe, (vSol * dt) / RAYON)
      g.quaternion.premultiply(rotation)
    }

  })

  function surLaPelouse(evenement: ThreeEvent<PointerEvent>) {
    evenement.ray.intersectPlane(solDeJeu, point)
    return point
  }

  function attraper(evenement: ThreeEvent<PointerEvent>) {
    evenement.stopPropagation()
    ;(evenement.target as Element).setPointerCapture?.(evenement.pointerId)
    const e = etat.current
    if (e.dansLeFilet) return
    e.tenu = true
    e.vitesse.set(0, 0, 0)
    e.derniers = []
  }

  function trainer(evenement: ThreeEvent<PointerEvent>) {
    const e = etat.current
    if (!e.tenu) return
    const p = surLaPelouse(evenement)
    // La vitesse du tir se calcule sur le geste réel du pointeur, pas sur la position bridée du ballon
    e.derniers.push({ p: p.clone(), t: performance.now() })
    if (e.derniers.length > 5) e.derniers.shift()
    // On ne porte pas le ballon jusqu'au but : passé la limite de sa moitié de terrain, c'est un tir
    const limite = depart.z - 2.5
    if (p.z < limite) {
      lacher()
      return
    }
    e.position.set(p.x, RAYON + 0.5, Math.min(LIMITES.zPres, p.z)) // soulevé pendant qu'on le tient
    ecran.copy(e.position).project(camera)
    if (Math.abs(ecran.x) > BORD_ECRAN) e.position.x *= BORD_ECRAN / Math.abs(ecran.x)
  }

  function lacher() {
    const e = etat.current
    if (!e.tenu) return
    e.tenu = false
    const a = e.derniers[0]
    const b = e.derniers[e.derniers.length - 1]
    if (a && b && b.t > a.t) {
      const s = (b.t - a.t) / 1000
      e.vitesse.set(((b.p.x - a.p.x) / s) * ELAN, 0, ((b.p.z - a.p.z) / s) * ELAN).clampLength(0, VITESSE_MAX)
      e.vitesse.y = Math.min(6, e.vitesse.length() * 0.3) // un tir fort décolle un peu
    }
  }

  return (
    <>
      <group ref={groupe} onPointerDown={attraper} onPointerMove={trainer} onPointerUp={lacher} onPointerCancel={lacher}>
        <BallonReel rayon={RAYON} />
        {/* zone de saisie un peu plus grande que le ballon : plus facile à attraper, surtout au doigt */}
        <mesh visible={false}>
          <sphereGeometry args={[RAYON * 1.6, 12, 12]} />
        </mesh>
      </group>
    </>
  )
}

/**
 * Le terrain de quartier, au coucher du soleil (ciel et lumière réels, HDRI). Sur ordinateur, il est décalé à droite (le texte est à gauche) ;
 * sur téléphone, centré et reculé vers le haut de l'image (le texte occupe le bas).
 */
function Terrain({ onBut }: { onBut: () => void }) {
  const { viewport } = useThree()
  const paysage = viewport.aspect > 1.1
  const stade = useMemo<Stade>(
    () =>
      paysage
        ? { cx: 3.6, zBut: -7.35, zPres: 8.35, demiLargeur: 6.5 }
        : { cx: 0, zBut: -9.4, zPres: 1.4, demiLargeur: 4.6 },
    [paysage],
  )
  // Proportions plus proches du réel : la cage fait plus de 6 fois la largeur du ballon
  // (sur téléphone, un peu plus petite pour tenir dans la largeur de l'écran, sous l'en-tête)
  const cage = useMemo<Cage>(
    () => ({
      x: stade.cx,
      z: stade.zBut + 0.35, // sur la ligne de but dessinée
      ...(paysage ? { largeur: 6, hauteur: 2.2, profondeur: 1.6 } : { largeur: 4.2, hauteur: 1.5, profondeur: 1.2 }),
    }),
    [stade, paysage],
  )
  const depart = useMemo(() => new Vector3(stade.cx, RAYON, (stade.zBut + stade.zPres) / 2), [stade])
  const horizon = useMemo(() => jeton('horizon'), [])
  return (
    <>
      {/* au loin, l'herbe se fond dans le vert du grillage de la photo : pas de cassure à l'horizon */}
      <fog attach="fog" args={[horizon, 22, 75]} />
      <Ciel />
      <Pelouse stade={stade} largeurCage={cage.largeur} />
      <Grillage stade={stade} coteGauche={!paysage} />
      <CageDeBut cage={cage} />
      <Ballon key={`${stade.cx}:${stade.zBut}`} depart={depart} cage={cage} stade={stade} onBut={onBut} />
    </>
  )
}

/** La scène, chargée à part (lazy) : caméra en tribune, au bord du terrain. */
export default function ScenePelouse({ onBut }: { onBut: () => void }) {
  // téléphone (écran en hauteur) : caméra plus haute, le terrain occupe le haut de l'écran, au-dessus du texte
  const portrait = window.innerWidth / window.innerHeight < 1.1
  return (
    <Canvas
      dpr={[1, 1.5]}
      shadows
      camera={portrait ? { position: [0, 6.5, 8], fov: 46 } : { position: [0, 3.6, 12.5], fov: 40 }}
      onCreated={({ camera }) => (portrait ? camera.lookAt(0, -3.2, -5) : camera.lookAt(0, 1.2, -3))}
      gl={{ antialias: true, powerPreference: 'low-power' }}
      className="touch-none"
    >
      <Terrain onBut={onBut} />
    </Canvas>
  )
}
