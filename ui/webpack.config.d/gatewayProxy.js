// The Hermes gateway lives on 127.0.0.1:9119 (loopback only) but the
// Pylon web page is served from 127.0.0.1:8080 locally and from
// https://serenity.oryx-galaxy.ts.net:8445 over the tailnet (tailscale
// serve -> 127.0.0.1:8080). The browser cannot reach the loopback
// gateway from the tailnet origin, and any cross-origin request to
// 127.0.0.1 from the tailnet origin would be CORS-blocked anyway.
//
// This file is merged into the webpack config the Kotlin wasm plugin
// generates, which is the supported way to override it. Add a dev
// proxy so same-origin /api/* requests transparently land at the
// gateway. The Ktor client in ui/src/wasmJsMain is pointed at the
// dev server's origin (http://127.0.0.1:8080) so this proxy is what
// reaches the gateway.
//
// v6 devServer takes proxy: [{ context, target, changeOrigin }]; the
// changeOrigin rewrite is what makes the gateway accept a Host header
// of "127.0.0.1:8080" rather than refusing the cross-origin-looking
// request.
config.devServer = config.devServer || {};
config.devServer.proxy = [
  {
    context: ['/api'],
    target: 'http://127.0.0.1:9119',
    changeOrigin: true,
    ws: false,
  },
];