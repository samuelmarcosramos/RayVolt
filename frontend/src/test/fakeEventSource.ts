export class FakeEventSource {
  static instances: FakeEventSource[] = []
  onopen: (() => void) | null = null
  onerror: (() => void) | null = null
  onmessage: ((event: MessageEvent<string>) => void) | null = null
  closed = false
  readonly url: string

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  open() {
    this.onopen?.()
  }

  fail() {
    this.onerror?.()
  }

  send(data: unknown) {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(data) }))
  }

  close() {
    this.closed = true
  }

  static latest(): FakeEventSource {
    return FakeEventSource.instances[FakeEventSource.instances.length - 1]
  }
}
