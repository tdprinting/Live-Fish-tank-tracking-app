# ReefLive push sender (iPhone tank alerts)

Safari can only show notifications for a web app that is added to the Home Screen (iOS 16.4+), and a closed page cannot
schedule anything. So when My tank is left, the page works out when the tank will next need you
(`core/.../custom/CareAlerts.kt`: hungry fish, full skimmer cup, dirty sock, low water, care jobs) and sends those times
here. This Cloudflare Worker (free plan) keeps them and, every 5 minutes, sends the due ones as Web Push. Opening the
app cancels them. Android schedules the same plan on the phone itself and needs none of this.

The worker keeps only the push address Apple gives the phone and a title and sentence per alert, for at most 5 days.

## One-time setup (needs the owner's Cloudflare account)
1. Make a free account at cloudflare.com. In this folder: `npm i -g wrangler` then `wrangler login`.
2. `wrangler kv namespace create QUEUE` and paste the printed id into `wrangler.toml`.
3. `node gen-keys.mjs` prints `VAPID_PUBLIC` and `VAPID_PRIVATE_JWK`. Then
   `wrangler secret put VAPID_PUBLIC` and `wrangler secret put VAPID_PRIVATE_JWK` (paste each value). Never commit them.
4. `wrangler deploy`. Copy the `https://reeflive-push.<you>.workers.dev` address into `web/site/push-config.js`
   (`PUSH_URL`) and merge that change.
5. On the iPhone: Safari > Share > Add to Home Screen, open ReefLive from the icon, then
   My tank > ⋯ > Tank alerts and allow notifications.

Test the crypto without Cloudflare: `node test/webpush.test.mjs`.
