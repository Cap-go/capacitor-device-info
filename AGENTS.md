# AGENTS.md

Guidance for agents and contributors working on this Capacitor plugin.

## Commands

```bash
bun install
bun run build
bun run verify
bun run fmt
bun run lint
```

Use Bun for local development commands in this repository. Documentation aimed at plugin users should use standard `npm` and `npx` commands.

## Workflow

1. Update `src/definitions.ts` first for public API changes.
2. Run `bun run docgen` or `bun run build` after JSDoc changes.
3. Keep iOS and Android behavior aligned when a metric exists on both platforms.
4. Run `bun run verify` before submitting changes.

## Project Structure

- `src/definitions.ts` - TypeScript API and generated README source.
- `src/web.ts` - Web fallback implementation.
- `ios/Sources/DeviceInfoPlugin/` - Swift native implementation.
- `android/src/main/java/app/capgo/deviceinfo/` - Android native implementation.
- `example-app/` - Vite app linked to the plugin with `file:..`.

## Native Notes

- iOS uses Metal for GPU info and Mach APIs for CPU/memory snapshots.
- Android uses ActivityManager, StatFs, `/proc`, and a cached OpenGL ES query.
- CPU usage is delta-based; first samples may omit `usagePercent`.
- Do not add runtime permissions unless a new metric truly requires one.

## Documentation

API docs in `README.md` are auto-generated between `<docgen-index>` and `<docgen-api>`. Do not edit those sections by hand.

## Versioning

Plugin major version follows Capacitor major version. New releases start at `8.0.0` for Capacitor 8.

## Timeout Policy

- Keep CI, script, and runtime timeouts at 10 minutes or less. Use `timeout-minutes: 10` or lower in GitHub Actions and cap timeout values at `600000` ms, `600` seconds, or `10m` unless explicitly requested.
