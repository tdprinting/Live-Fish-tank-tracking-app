// ReefLive web: notes every Kotlin class as the compiled Kotlin code defines it (simwire.js sends objects between the
// sim worker and the page as "class number + fields"). Must be imported before kotlin/ReefLive-web.mjs, on the page
// and in the worker alike: both then load the same classes in the same order and number them the same.
//
// Kotlin/JS (kotlin-stdlib initMetadataFor) stores each class's metadata as `ctor.$metadata$ = ...`; a setter on
// Function.prototype sees every one of those assignments, in definition order.
const ctors = globalThis.__reefCtors || (globalThis.__reefCtors = []);
if (!Object.getOwnPropertyDescriptor(Function.prototype, '$metadata$')) {
  Object.defineProperty(Function.prototype, '$metadata$', {
    configurable: true,
    get() { return undefined; },
    set(v) {
      Object.defineProperty(this, '$metadata$', { value: v, writable: true, configurable: true, enumerable: true });
      ctors.push(this);
    },
  });
}
export default ctors;
