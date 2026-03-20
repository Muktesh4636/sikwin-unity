# Server infrastructure – gunduata.club

The live website and API are served at **https://gunduata.club**. This document records the server layout and how to access machines for deployment and maintenance.

---

## Servers

| Server   | IP             | Role                                                                 |
|----------|----------------|----------------------------------------------------------------------|
| **LB**   | `187.77.186.84` | Nginx, **gunduata.club**; forwards to App 1/2/3 on port **8001**     |
| **App 1**| `72.61.254.71` | web, game_timer, bet_worker                                          |
| **App 2**| `72.61.254.74` | web, game_timer, bet_worker, **Redis**                               |
| **App 3**| `72.62.226.41` | web, game_timer, bet_worker                                          |
| **DB**   | `72.61.255.231`| PostgreSQL, port **6432**                                            |

### Details

- **Domain:** `gunduata.club` is configured on the Load Balancer (LB).
- **Traffic:** LB proxies to the three app servers (71, 74, 41) on **port 8001**.
- **App services:** Each app server runs `web`, `game_timer`, and `bet_worker`.
- **Redis:** Hosted on **App 2** (`72.61.254.74`).
- **Database:** PostgreSQL on **DB** at port **6432**.

---

## Access

- **SSH user:** (use your usual SSH user, e.g. `root` or `ubuntu`, per server.)
- **Password for all servers:** `Gunduata@123`

**Security:** Do not commit this password to the repo. Prefer SSH keys and store the password in a secrets manager or local `.env` that is gitignored. This file is for team reference; consider moving credentials to a secure vault in production.

---

## Website and API

- **Frontend / API base:** The website is configured to use `https://gunduata.club/api/` as the API base (see [website/README.md](../website/README.md)).
- **Deploying the website:** Build the static site (e.g. `npm run build` in `website/`) and serve the `dist/` output from the app servers or from the LB, depending on your Nginx setup.
