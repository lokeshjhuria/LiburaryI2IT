# I2IT Library

A lightweight Java website for managing the I2IT campus library. It runs on the JDK with no framework or external dependencies; catalog and checkout data are stored locally in CSV files.

## Run Locally

Install a JDK 17 or newer, open this folder in terminal, and run:

```bash
javac --add-modules jdk.httpserver Main.java
java --add-modules jdk.httpserver Main
```

Then open [http://localhost:8080](http://localhost:8080). To use another port, set the `PORT` environment variable or pass it after `Main` (e.g. `java --add-modules jdk.httpserver Main 8081`).

## Deploy with Vercel

This repository is configured for **Vercel Container Deployment** using the included `Dockerfile` and `vercel.json`.

1. Push your code to your GitHub repository:
   ```bash
   git add .
   git commit -m "Configure project for Vercel deployment"
   git push origin main
   ```
2. Go to your [Vercel Dashboard](https://vercel.com).
3. Click **Add New...** -> **Project** and import your `LiburaryI2IT` repository.
4. Vercel automatically detects the `Dockerfile` and container preset (`framework: "container"`).
5. Click **Deploy**.

> **Note on Data Storage:** Because serverless container environments (like Vercel) have ephemeral/read-only filesystems outside `/tmp`, runtime changes are kept in `/tmp/data` initialized from your repository CSVs. For permanent production data across re-deploys and multiple instances, consider connecting to an external database or persistent volume.

## Features

- Dashboard with catalog, available-copy, active-checkout, and overdue counts
- Search by title, author, or ISBN and filter by category
- Add books, check books out to students for 14 days, and record returns
- Remove books after all copies have been returned
- Register an account, sign in, and sign out; passwords are stored as salted PBKDF2 hashes
- Persistent CSV data and a starter collection for first-time use

The first visit opens the account sign-up page. Account records are stored in `accounts.csv`. Sessions last up to 12 hours or until sign-out.
