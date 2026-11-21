export class Deferred<T> {
  promise: Promise<T>
  resolve!: (value: T | PromiseLike<T>) => void
  reject!: (reason?: unknown) => void
  state: 'pending' | 'fulfilled' | 'rejected' = 'pending'
  reason?: unknown

  constructor() {
    this.promise = new Promise((resolve, reject) => {
      this.resolve = (value: T | PromiseLike<T>) => {
        this.state = 'fulfilled'
        resolve(value)
      }
      this.reject = (reason?: unknown) => {
        this.state = 'rejected'
        this.reason = reason
        reject(reason)
      }
    })
  }
}
