// webpack-dev-server rejects requests whose Host header it does not recognise,
// answering 403 "Invalid Host header" (a 19-byte body). That is what blocks the
// tailscale serve URL (serenity.oryx-galaxy.ts.net:8445 -> 127.0.0.1:8080): the
// proxy forwards the tailnet hostname, not localhost.
//
// This file is merged into the webpack config the Kotlin wasm plugin generates,
// which is the supported way to override it. Allow the tailnet domain rather than
// '*' so the dev server stays closed to arbitrary hosts.
config.devServer = config.devServer || {};
config.devServer.allowedHosts = ['.ts.net', 'localhost', '127.0.0.1'];
