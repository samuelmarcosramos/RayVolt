import { type RefObject, useLayoutEffect, useRef } from 'react'
import Logo from './Logo'

type FleetIntroProps = {
  title: RefObject<HTMLHeadingElement | null>
  onDone: () => void
}

const SIZE = 160
const SCALE = SIZE / 100
const WHEELS = [26, 78]
const WHEEL_Y = 76
const OUTER = 23 * SCALE
const STROKE = 7 * SCALE

const EASE_OUT = 'cubic-bezier(0.2, 0.8, 0.2, 1)'
const EASE_IN_OUT = 'cubic-bezier(0.65, 0, 0.35, 1)'
const FLIGHT = 1000

function inkCenter(target: HTMLElement) {
  const style = getComputedStyle(target)
  const context = document.createElement('canvas').getContext('2d')
  const rect = target.getBoundingClientRect()
  if (!context) {
    return { x: rect.width / 2, y: rect.height * 0.6, size: rect.height * 0.55 }
  }
  context.font = style.font
  const metrics = context.measureText('e')
  const marker = document.createElement('span')
  marker.style.cssText = 'display:inline-block;width:0;height:0;vertical-align:baseline'
  target.appendChild(marker)
  const baseline = marker.getBoundingClientRect().bottom - rect.top
  marker.remove()
  const left = -metrics.actualBoundingBoxLeft
  const right = metrics.actualBoundingBoxRight
  const top = baseline - metrics.actualBoundingBoxAscent
  const bottom = baseline + metrics.actualBoundingBoxDescent
  return { x: (left + right) / 2, y: (top + bottom) / 2, size: Math.max(right - left, bottom - top) }
}

function FleetIntro({ title, onDone }: FleetIntroProps) {
  const layerRef = useRef<HTMLDivElement>(null)
  const stageRef = useRef<HTMLDivElement>(null)
  const bodyRef = useRef<HTMLDivElement>(null)
  const wheelRefs = useRef<(HTMLSpanElement | null)[]>([])
  const spokeRefs = useRef<(HTMLSpanElement | null)[]>([])

  useLayoutEffect(() => {
    const layer = layerRef.current
    const stage = stageRef.current
    const body = bodyRef.current
    const heading = title.current
    const wheels = wheelRefs.current
    const spokes = spokeRefs.current
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
    if (!layer || !stage || !body || !heading || typeof stage.animate !== 'function' || reduced) {
      onDone()
      return
    }
    const letters = Array.from(heading.querySelectorAll<HTMLElement>('.fleet-letter'))
    const targets = Array.from(heading.querySelectorAll<HTMLElement>('.fleet-e'))
    const running: Animation[] = []
    const fliers: HTMLElement[] = []
    let cancelled = false

    const play = (element: Element, frames: Keyframe[], options: KeyframeAnimationOptions) => {
      const animation = element.animate(frames, { fill: 'both', ...options })
      running.push(animation)
      return animation.finished
    }

    const fly = (wheel: HTMLElement, target: HTMLElement, delay: number) => {
      const style = getComputedStyle(target)
      const rect = target.getBoundingClientRect()
      const ink = inkCenter(target)
      const start = wheel.getBoundingClientRect()
      const scale = OUTER / ink.size

      const flier = document.createElement('span')
      flier.className = 'fleet-intro-flier'
      Object.assign(flier.style, {
        left: `${rect.left}px`,
        top: `${rect.top}px`,
        width: `${rect.width}px`,
        height: `${rect.height}px`,
        font: style.font,
        letterSpacing: style.letterSpacing,
        color: style.color,
        transformOrigin: `${ink.x}px ${ink.y}px`,
      })

      const glyph = document.createElement('span')
      glyph.className = 'fleet-intro-glyph'
      glyph.textContent = 'e'

      const ring = document.createElement('span')
      ring.className = 'fleet-intro-ring'
      Object.assign(ring.style, {
        left: `${ink.x - ink.size / 2}px`,
        top: `${ink.y - ink.size / 2}px`,
        width: `${ink.size}px`,
        height: `${ink.size}px`,
        borderWidth: `${STROKE / scale}px`,
        color: getComputedStyle(stage).color,
      })
      const spoke = document.createElement('span')
      spoke.className = 'fleet-intro-spoke'
      spoke.style.height = `${(STROKE * 0.7) / scale}px`
      spoke.style.opacity = '1'
      ring.appendChild(spoke)

      flier.append(glyph, ring)
      layer.appendChild(flier)
      fliers.push(flier)
      wheel.style.visibility = 'hidden'

      const dx = start.left + start.width / 2 - (rect.left + ink.x)
      const dy = start.top + start.height / 2 - (rect.top + ink.y)
      const timing = { duration: FLIGHT, delay, easing: EASE_IN_OUT }
      return Promise.all([
        play(flier, [
          { transform: `translate(${dx}px, ${dy}px) scale(${scale}) rotate(-720deg)` },
          { transform: 'translate(0, 0) scale(1) rotate(0deg)' },
        ], timing),
        play(ring, [{ opacity: 1 }, { opacity: 1, offset: 0.3 }, { opacity: 0, offset: 0.7 }, { opacity: 0 }], timing),
        play(glyph, [{ opacity: 0 }, { opacity: 0, offset: 0.3 }, { opacity: 1, offset: 0.7 }, { opacity: 1 }], timing),
      ])
    }

    const run = async () => {
      await play(stage, [
        { opacity: 0, transform: 'scale(0.6)' },
        { opacity: 1, transform: 'scale(1)' },
      ], { duration: 550, easing: EASE_OUT })
      await Promise.all(spokes.map((spoke) =>
        spoke && play(spoke, [{ opacity: 0 }, { opacity: 1 }], { duration: 200 })))
      await document.fonts?.ready
      if (cancelled) {
        return
      }

      const flights = wheels.map((wheel, index) =>
        wheel ? fly(wheel, targets[index], index * 90) : Promise.resolve())
      const extras = [
        play(body, [{ opacity: 1 }, { opacity: 0, transform: 'scale(0.9)' }], { duration: 400, easing: EASE_OUT }),
        ...letters.map((letter, index) =>
          play(letter, [
            { opacity: 0, transform: 'translateY(0.3em)' },
            { opacity: 1, transform: 'none' },
          ], { duration: 450, delay: 500 + index * 60, easing: EASE_OUT })),
      ]
      await Promise.all([...flights, ...extras])

      targets.forEach((target) => play(target, [{ opacity: 1 }, { opacity: 1 }], { duration: 0 }))
      fliers.forEach((flier) => flier.remove())
    }

    run()
      .then(() => {
        if (!cancelled) {
          onDone()
        }
      })
      .catch(() => {})

    return () => {
      cancelled = true
      running.forEach((animation) => animation.cancel())
      fliers.forEach((flier) => flier.remove())
      wheels.forEach((wheel) => wheel?.style.removeProperty('visibility'))
    }
  }, [title, onDone])

  return (
    <div className="fleet-intro" ref={layerRef} aria-hidden="true">
      <div className="fleet-intro-stage" ref={stageRef} style={{ width: SIZE, height: SIZE }}>
        <div className="fleet-intro-body" ref={bodyRef}>
          <Logo className="fleet-intro-logo" wheels={false} />
        </div>
        {WHEELS.map((x, index) => (
          <span
            key={x}
            className="fleet-intro-ring"
            ref={(element) => {
              wheelRefs.current[index] = element
            }}
            style={{
              left: x * SCALE - OUTER / 2,
              top: WHEEL_Y * SCALE - OUTER / 2,
              width: OUTER,
              height: OUTER,
              borderWidth: STROKE,
            }}
          >
            <span
              className="fleet-intro-spoke"
              ref={(element) => {
                spokeRefs.current[index] = element
              }}
              style={{ height: STROKE * 0.7 }}
            />
          </span>
        ))}
      </div>
    </div>
  )
}

export default FleetIntro
