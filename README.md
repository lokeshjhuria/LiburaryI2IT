# I2IT Library

A lightweight Java website for managing the I2IT campus library. It runs on the JDK with no framework or external dependencies; catalog and checkout data are stored locally in `data\books.csv` and `data\loans.csv`.

## Run

Install a JDK 17 or newer, open this folder in PowerShell, and run:

```powershell
javac --add-modules jdk.httpserver Main.java
java --add-modules jdk.httpserver Main
```

Then open [http://localhost:8080](http://localhost:8080). To use another port, pass it after `Main`, for example `java --add-modules jdk.httpserver Main 8081`.

## Features

- Dashboard with catalog, available-copy, active-checkout, and overdue counts
- Search by title, author, or ISBN and filter by category
- Add books, check books out to students for 14 days, and record returns
- Remove books after all copies have been returned
- Register an account, sign in, and sign out; passwords are stored as salted PBKDF2 hashes
- Persistent CSV data and a starter collection for first-time use

The first visit opens the account sign-up page. Account records are stored in `data\accounts.csv`. Sessions last up to 12 hours or until sign-out.
