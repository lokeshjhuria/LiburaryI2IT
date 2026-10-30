const http = require('http');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const url = require('url');

// Data structures
let books = [
  { id: 1, title: "The Pragmatic Programmer", author: "David Thomas & Andrew Hunt", isbn: "978-0135957059", category: "Technology", copies: 5, available: 4 },
  { id: 2, title: "Clean Code", author: "Robert C. Martin", isbn: "978-0132350884", category: "Technology", copies: 4, available: 3 },
  { id: 3, title: "Design of Everyday Things", author: "Don Norman", isbn: "978-0465050659", category: "Design", copies: 3, available: 3 },
  { id: 4, title: "Wings of Fire", author: "A. P. J. Abdul Kalam", isbn: "978-8173711466", category: "Biography", copies: 6, available: 5 },
  { id: 5, title: "The Alchemist", author: "Paulo Coelho", isbn: "978-0062315007", category: "Fiction", copies: 4, available: 2 },
  { id: 6, title: "Introduction to Algorithms", author: "Thomas H. Cormen", isbn: "978-0262046305", category: "Technology", copies: 3, available: 2 },
  { id: 7, title: "Atomic Habits", author: "James Clear", isbn: "978-0735211292", category: "Self development", copies: 5, available: 5 },
  { id: 8, title: "Sapiens", author: "Yuval Noah Harari", isbn: "978-0062316097", category: "History", copies: 3, available: 2 },
  { id: 9, title: "oops", author: "lokesh", isbn: "ISBN-12", category: "technology", copies: 100, available: 99 }
];

let loans = [
  { id: 1, bookId: 1, title: "The Pragmatic Programmer", student: "Aarav Patil · CSE-2041", dueDate: "2026-11-11", returned: false },
  { id: 2, bookId: 4, title: "Wings of Fire", student: "Mira Shah · IT-2038", dueDate: "2026-11-07", returned: false },
  { id: 3, bookId: 5, title: "The Alchemist", student: "Ishaan Joshi · ENTC-2027", dueDate: "2026-11-03", returned: false },
  { id: 4, bookId: 2, title: "Clean Code", student: "TEST-STUDENT", dueDate: "2026-11-13", returned: true },
  { id: 5, bookId: 1, title: "The Pragmatic Programmer", student: "12", dueDate: "2026-11-13", returned: true },
  { id: 6, bookId: 9, title: "oops", student: "IT201", dueDate: "2026-11-13", returned: false }
];

let accounts = new Map([
  ["lokeshjhuria7@gmail.com", {
    name: "lokesh",
    email: "lokeshjhuria7@gmail.com",
    salt: "lHOAxRpC77GNTnNeHywVxQ==",
    passwordHash: "juNljt8tuNJXMj5PDCOQT6UeFajknv7Q9FRsePU9Bcg="
  }]
]);

let sessions = new Map();
let nextBookId = 10;
let nextLoanId = 7;

function loadCsvData() {
  const findFile = (name) => {
    const candidates = [
      path.join(process.cwd(), 'data', name),
      path.join(process.cwd(), name),
      path.join(__dirname, '..', 'data', name),
      path.join(__dirname, '..', name)
    ];
    for (const c of candidates) {
      if (fs.existsSync(c)) return c;
    }
    return null;
  };

  const parseCsvLine = (line) => {
    const cells = [];
    let cell = '';
    let quoted = false;
    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (ch === '"') {
        if (quoted && i + 1 < line.length && line[i + 1] === '"') {
          cell += '"';
          i++;
        } else {
          quoted = !quoted;
        }
      } else if (ch === ',' && !quoted) {
        cells.push(cell);
        cell = '';
      } else {
        cell += ch;
      }
    }
    cells.push(cell);
    return cells;
  };

  const bPath = findFile('books.csv');
  if (bPath) {
    try {
      const lines = fs.readFileSync(bPath, 'utf8').split(/\r?\n/).filter(l => l.trim().length > 0);
      const parsed = [];
      for (const line of lines) {
        const row = parseCsvLine(line);
        if (row.length === 7) {
          const id = parseInt(row[0], 10);
          parsed.push({
            id,
            title: row[1],
            author: row[2],
            isbn: row[3],
            category: row[4],
            copies: parseInt(row[5], 10),
            available: parseInt(row[6], 10)
          });
          nextBookId = Math.max(nextBookId, id + 1);
        }
      }
      if (parsed.length > 0) books = parsed;
    } catch (e) {}
  }

  const lPath = findFile('loans.csv');
  if (lPath) {
    try {
      const lines = fs.readFileSync(lPath, 'utf8').split(/\r?\n/).filter(l => l.trim().length > 0);
      const parsed = [];
      for (const line of lines) {
        const row = parseCsvLine(line);
        if (row.length === 6) {
          const id = parseInt(row[0], 10);
          parsed.push({
            id,
            bookId: parseInt(row[1], 10),
            title: row[2],
            student: row[3],
            dueDate: row[4],
            returned: row[5].toLowerCase() === 'true'
          });
          nextLoanId = Math.max(nextLoanId, id + 1);
        }
      }
      if (parsed.length > 0) loans = parsed;
    } catch (e) {}
  }

  const aPath = findFile('accounts.csv');
  if (aPath) {
    try {
      const lines = fs.readFileSync(aPath, 'utf8').split(/\r?\n/).filter(l => l.trim().length > 0);
      for (const line of lines) {
        const row = parseCsvLine(line);
        if (row.length === 4) {
          accounts.set(row[1].toLowerCase(), {
            name: row[0],
            email: row[1].toLowerCase(),
            salt: row[2],
            passwordHash: row[3]
          });
        }
      }
    } catch (e) {}
  }
}

loadCsvData();

function escape(text) {
  if (text == null) return '';
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function hashPassword(password, saltBase64) {
  const salt = Buffer.from(saltBase64, 'base64');
  return crypto.pbkdf2Sync(password, salt, 120000, 32, 'sha256').toString('base64');
}

function parseForm(raw) {
  const result = {};
  if (!raw) return result;
  const params = new URLSearchParams(raw);
  for (const [key, value] of params.entries()) {
    result[key] = value;
  }
  return result;
}

function getSessionEmail(req) {
  const cookieHeader = req.headers.cookie;
  if (!cookieHeader) return null;
  const cookies = cookieHeader.split(';');
  for (const c of cookies) {
    const [k, v] = c.trim().split('=');
    if (k === 'I2IT_SESSION') {
      return sessions.get(v) || null;
    }
  }
  return null;
}

function redirect(res, location, extraHeaders = {}) {
  res.writeHead(303, {
    Location: location,
    ...extraHeaders
  });
  res.end();
}

function sendHtml(res, status, html) {
  const buf = Buffer.from(html, 'utf8');
  res.writeHead(status, {
    'Content-Type': 'text/html; charset=utf-8',
    'Content-Length': buf.length,
    'X-Content-Type-Options': 'nosniff'
  });
  res.end(buf);
}

function renderAuth(createAccount, message) {
  const title = createAccount ? "Create your account" : "Welcome back";
  const description = createAccount
    ? "Join the I2IT campus library and keep knowledge moving."
    : "Sign in to continue to your campus library.";

  let html = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title} · I2IT Library</title><style>
:root{--ink:#17201d;--muted:#75817a;--green:#226b50;--green-dark:#174b39;--paper:#f5f7f1;--lime:#d8f07d;--line:#e7ebe3}
*{box-sizing:border-box}body{margin:0;min-height:100vh;background:var(--paper);color:var(--ink);font:14px/1.5 "Segoe UI",Arial,sans-serif;display:grid;place-items:center;padding:24px}
.auth-layout{width:min(920px,100%);min-height:540px;display:grid;grid-template-columns:1fr 1fr;background:white;border:1px solid var(--line);border-radius:20px;overflow:hidden;box-shadow:0 18px 55px #18352712}
.auth-brand{background:#173d32;color:#eef5ed;padding:42px;display:flex;flex-direction:column;justify-content:space-between;position:relative;overflow:hidden}
.auth-brand:after{content:"";position:absolute;width:330px;height:330px;border:1px solid #ffffff20;border-radius:50%;right:-130px;bottom:-115px;box-shadow:0 0 0 38px #ffffff08,0 0 0 78px #ffffff06}
.brand{display:flex;align-items:center;gap:11px;position:relative;z-index:1}.brand-mark{width:39px;height:39px;border-radius:12px;background:var(--lime);color:#163c30;display:grid;place-items:center;font-size:21px;font-weight:800}
.brand strong{font-size:15px}.brand small{display:block;color:#a8c2b5;font-size:11px;margin-top:1px}
.brand-copy{position:relative;z-index:1;max-width:330px;margin:55px 0}.brand-copy .eyebrow{font-size:10px;text-transform:uppercase;letter-spacing:.16em;color:#b8d29b;font-weight:700}
.brand-copy h2{font-size:34px;line-height:1.16;letter-spacing:-.04em;margin:12px 0}.brand-copy p{color:#bed0c4;font-size:13px;max-width:290px}
.brand-foot{position:relative;z-index:1;color:#a8c2b5;font-size:11px}.auth-form-wrap{padding:42px clamp(25px,6vw,65px);display:flex;flex-direction:column;justify-content:center}
.form-eyebrow{font-size:10px;color:var(--green);font-weight:700;text-transform:uppercase;letter-spacing:.13em}.auth-form-wrap h1{font-size:27px;letter-spacing:-.04em;margin:7px 0 4px}
.auth-form-wrap .description{color:var(--muted);font-size:12px;margin:0 0 24px}.auth-form{display:grid;gap:14px}
.auth-form label{display:grid;gap:6px;color:#46524a;font-size:11px;font-weight:600}.auth-form input{width:100%;padding:11px 12px;border:1px solid var(--line);border-radius:8px;background:#fff;color:var(--ink);font:13px "Segoe UI",Arial,sans-serif;outline:none}
.auth-form input:focus{border-color:#82aa8c;box-shadow:0 0 0 3px #226b5012}.auth-form input::placeholder{color:#aab1ab}
.primary{border:0;background:var(--green);color:white;border-radius:8px;padding:11px 14px;font-size:12px;font-weight:700;cursor:pointer;margin-top:3px}.primary:hover{background:var(--green-dark)}
.switch{margin-top:20px;color:var(--muted);font-size:11px;text-align:center}.switch a{color:var(--green);font-weight:700;text-decoration:none}.switch a:hover{text-decoration:underline}
.flash{padding:10px 12px;margin:0 0 15px;border-radius:8px;background:#fff1ed;color:#9c4438;font-size:11px}
@media(max-width:680px){body{padding:12px}.auth-layout{grid-template-columns:1fr;min-height:0}.auth-brand{padding:20px 22px}.brand-copy,.brand-foot{display:none}.auth-form-wrap{padding:35px 25px 30px}.auth-form-wrap h1{font-size:24px}}
</style></head><body><div class="auth-layout"><section class="auth-brand"><div class="brand"><div class="brand-mark">i</div><div><strong>I2IT Library</strong><small>Knowledge, organized.</small></div></div>
<div class="brand-copy"><div class="eyebrow">Campus library · Hinjawadi, Pune</div><h2>A little curiosity goes a long way.</h2><p>Find your next great read, explore new ideas, and keep the I2IT community learning together.</p></div>
<div class="brand-foot">International Institute of Information Technology · Est. 2003</div></section><main class="auth-form-wrap">
<div class="form-eyebrow">I2IT campus library</div><h1>${title}</h1><p class="description">${description}</p>`;

  if (message) html += `<div class="flash">${escape(message)}</div>`;

  html += `<form class="auth-form" method="post" action="${createAccount ? '/register' : '/login'}">`;
  if (createAccount) {
    html += `<label>Your name<input name="name" type="text" autocomplete="name" placeholder="e.g. Ananya Deshmukh" maxlength="80" required></label>`;
  }
  html += `<label>Email address<input name="email" type="email" autocomplete="email" placeholder="you@i2it.edu" maxlength="254" required></label>
<label>Password<input name="password" type="password" autocomplete="${createAccount ? 'new-password' : 'current-password'}" placeholder="${createAccount ? 'At least 8 characters' : 'Enter your password'}" minlength="${createAccount ? '8' : '1'}" maxlength="128" required></label>
<button class="primary" type="submit">${createAccount ? 'Create account' : 'Sign in'}</button></form>
<div class="switch">${createAccount ? 'Already have an account? <a href="/login">Sign in</a>' : 'New to the library? <a href="/register">Create an account</a>'}</div>
</main></div></body></html>`;

  return html;
}

function render(query, accountName) {
  const search = (query.q || '').trim();
  const category = query.category || '';

  const filtered = books.filter(b => {
    const matchesSearch = !search || `${b.title} ${b.author} ${b.isbn}`.toLowerCase().includes(search.toLowerCase());
    const matchesCat = !category || b.category === category;
    return matchesSearch && matchesCat;
  });

  const availableTitles = books.filter(b => b.available > 0).length;
  const activeLoans = loans.filter(l => !l.returned).length;
  const today = new Date().toISOString().split('T')[0];
  const overdue = loans.filter(l => !l.returned && l.dueDate < today).length;
  const categories = [...new Set(books.map(b => b.category))].sort();

  const stat = (label, number, note, icon) => `
    <article class="stat"><div class="stat-top">${label}<span class="stat-icon">${icon}</span></div>
    <div class="stat-number">${number}</div><div class="stat-note">${note}</div></article>`;

  const field = (name, label, placeholder, type) => `
    <label>${label}<input name="${name}" type="${type}" placeholder="${placeholder}" ${name === 'copies' ? 'min="1" max="999"' : 'required'} required></label>`;

  let html = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>I2IT Library · Library management</title><style>
:root{--ink:#17201d;--muted:#7a8580;--paper:#f6f7f3;--white:#fff;--green:#226b50;--green-dark:#174b39;--line:#e8ebe5;--lime:#d8f07d;--red:#b95143}
*{box-sizing:border-box}body{margin:0;background:var(--paper);color:var(--ink);font:14px/1.5 "Segoe UI",Arial,sans-serif}
button,input,select{font:inherit}button,.button{cursor:pointer}.layout{min-height:100vh;display:grid;grid-template-columns:246px 1fr}
aside{background:#173d32;color:#edf4ed;padding:27px 19px;display:flex;flex-direction:column;gap:32px}
.brand{display:flex;align-items:center;gap:11px;padding:2px 6px}.brand-mark{width:39px;height:39px;border-radius:12px;background:var(--lime);color:#163c30;display:grid;place-items:center;font-size:21px;font-weight:800}
.brand strong{font-size:15px;letter-spacing:.01em}.brand small{display:block;color:#a8c2b5;font-size:11px;margin-top:1px}
.nav-label{font-size:10px;text-transform:uppercase;letter-spacing:.15em;color:#9bb6a9;padding:0 10px 9px}.nav-link{display:flex;align-items:center;gap:12px;color:#d9e7dd;text-decoration:none;padding:11px 12px;border-radius:9px;margin:2px 0;font-size:13px}
.nav-link.active{background:#2c5948;color:white}.nav-icon{font-size:17px;width:19px;text-align:center}.side-bottom{margin-top:auto;border-top:1px solid #ffffff24;padding:20px 8px 0;color:#a8c2b5;font-size:11px}.side-bottom strong{display:block;color:#e5eee7;font-size:12px;margin-bottom:4px}
main{min-width:0;padding:31px clamp(20px,4vw,55px) 48px;max-width:1600px;width:100%;margin:0 auto}.topline{display:flex;justify-content:space-between;align-items:center;gap:20px;margin-bottom:28px}
.crumb{color:var(--muted);font-size:12px}.crumb b{color:var(--ink);font-weight:600}.user{display:flex;align-items:center;gap:10px;font-size:12px}.avatar{width:35px;height:35px;border-radius:50%;background:#e8eedb;color:var(--green-dark);display:grid;place-items:center;font-weight:700}
.welcome{display:flex;justify-content:space-between;align-items:flex-end;gap:16px;margin-bottom:25px}.eyebrow{color:var(--green);font-size:11px;font-weight:700;letter-spacing:.12em;text-transform:uppercase}
h1{font-size:30px;letter-spacing:-.04em;margin:4px 0 3px;line-height:1.2}.sub{color:var(--muted);margin:0;font-size:13px}.today{color:var(--muted);font-size:12px}
.stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:15px;margin-bottom:24px}.stat{padding:18px 19px;background:var(--white);border:1px solid var(--line);border-radius:12px}
.stat-top{display:flex;justify-content:space-between;align-items:center;color:var(--muted);font-size:12px}.stat-icon{width:30px;height:30px;border-radius:9px;background:#eff5e6;color:var(--green);display:grid;place-items:center;font-size:15px}
.stat-number{font-size:27px;font-weight:700;letter-spacing:-.04em;margin:9px 0 2px}.stat-note{font-size:11px;color:var(--muted)}.stat-note.good{color:var(--green)}
.grid{display:grid;grid-template-columns:minmax(0,1.65fr) minmax(270px,.9fr);gap:17px;align-items:start}.panel{background:var(--white);border:1px solid var(--line);border-radius:13px;overflow:hidden}
.panel-head{padding:18px 20px 14px;display:flex;align-items:center;justify-content:space-between;gap:12px}.panel-head h2{margin:0;font-size:15px;letter-spacing:-.02em}.panel-head p{margin:3px 0 0;color:var(--muted);font-size:11px}
.count{font-size:11px;color:var(--muted);background:#f2f4ef;padding:5px 9px;border-radius:20px}.toolbar{padding:0 20px 14px;display:flex;gap:8px}
.search{position:relative;flex:1}.search input{padding-left:34px;width:100%}.search span{position:absolute;left:11px;top:7px;color:var(--muted);font-size:15px}
input,select{border:1px solid var(--line);border-radius:7px;padding:8px 10px;color:var(--ink);background:#fff;outline:none;font-size:12px}input:focus,select:focus{border-color:#82aa8c;box-shadow:0 0 0 3px #226b5012}
.filter{min-width:112px}.primary{border:0;background:var(--green);color:white;border-radius:7px;padding:8px 13px;font-size:12px;font-weight:600}.primary:hover{background:var(--green-dark)}
.add-trigger{background:var(--lime);color:#1b3529}.add-trigger:hover{background:#c9e76b}.table-wrap{overflow-x:auto}table{width:100%;border-collapse:collapse;text-align:left;white-space:nowrap}th{font-size:10px;color:var(--muted);font-weight:600;text-transform:uppercase;letter-spacing:.07em;padding:10px 13px;background:#fafbf8;border-top:1px solid var(--line);border-bottom:1px solid var(--line)}
td{padding:12px 13px;border-bottom:1px solid #eff0ec;font-size:12px}tr:last-child td{border-bottom:0}.book-name{font-weight:600;font-size:12px}.book-author{font-size:10px;color:var(--muted);margin-top:2px}.badge{display:inline-block;border-radius:20px;background:#f0f3eb;padding:4px 8px;font-size:10px;color:#58635a}
.availability{font-size:11px}.availability b{color:var(--green)}.availability .none{color:var(--red)}.action{border:1px solid var(--line);background:#fff;color:var(--green-dark);border-radius:6px;padding:5px 9px;font-size:10px;font-weight:600}.action:hover{background:#f4f8ef}.row-action{position:relative}
details.borrow{position:relative}details.borrow summary{list-style:none}details.borrow summary::-webkit-details-marker{display:none}details.borrow[open] .borrow-form{display:grid}
.borrow-form{display:none;position:absolute;z-index:5;right:0;top:29px;width:210px;padding:12px;background:#fff;border:1px solid var(--line);border-radius:9px;box-shadow:0 8px 24px #17201d1c;gap:8px}.borrow-form label,.add-form label{font-size:11px;color:var(--muted);display:grid;gap:4px}
.side-panel .panel-head{padding-bottom:10px}.loan-list{padding:0 18px}.loan{padding:12px 0;border-top:1px solid var(--line);display:grid;grid-template-columns:35px 1fr auto;align-items:center;gap:10px}
.loan-art{width:34px;height:40px;border-radius:4px;background:#e6edda;color:var(--green-dark);display:grid;place-items:center;font-size:15px}.loan:nth-child(2) .loan-art{background:#f3e5d7;color:#9a6438}.loan:nth-child(3) .loan-art{background:#e3e6f4;color:#59639b}
.loan-title{font-size:11px;font-weight:600;white-space:normal}.loan-meta{font-size:10px;color:var(--muted);margin-top:3px}.due{font-size:9px;color:#6b756d;margin-top:3px}.return-form button{background:none;border:0;color:var(--green);font-size:10px;padding:3px}
.overdue-note{margin:7px 18px 16px;padding:10px 12px;background:#fff5ee;color:#8f5742;border-radius:8px;font-size:10px}.empty{padding:30px 16px;text-align:center;color:var(--muted);font-size:12px}
.flash{padding:10px 14px;margin:0 0 17px;border-radius:8px;background:#edf6e7;color:#285d39;font-size:12px}.flash.error{background:#fff0eb;color:#9c4438}
.modal{margin:0 20px 16px;padding:14px;background:#f7f9f4;border:1px solid var(--line);border-radius:10px}.modal summary{font-size:12px;font-weight:600;color:var(--green-dark);cursor:pointer}
.add-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:13px}.add-form input{width:100%}.add-form .primary{grid-column:1/-1}
.remove-form{display:inline}.remove-form button{border:0;background:none;color:#a65b4c;font-size:10px;cursor:pointer;padding:4px}
footer{font-size:10px;color:#9aa39c;text-align:center;margin-top:23px}
@media(max-width:1050px){.layout{grid-template-columns:205px 1fr}.grid{grid-template-columns:1fr}.side-panel{display:grid;grid-template-columns:1fr 1fr}.side-panel .panel-head{grid-column:1/-1}.overdue-note{align-self:start}}
@media(max-width:760px){.layout{grid-template-columns:1fr}aside{padding:12px 15px;gap:12px;flex-direction:row;align-items:center;position:sticky;top:0;z-index:10}.brand{padding:0}.brand-mark{width:34px;height:34px}.nav-label,.nav-link:not(.active),.side-bottom{display:none}.nav-link.active{margin-left:auto;padding:8px 11px;font-size:11px}.nav-icon{display:none}main{padding:23px 15px 35px}.stats{grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.stat{padding:14px}.stat-number{font-size:23px}.welcome{align-items:flex-start;flex-direction:column;gap:8px}.today{display:none}.topline{margin-bottom:20px}.side-panel{display:block}.toolbar{padding:0 12px 12px;flex-wrap:wrap}.search{min-width:100%}.filter{flex:1}.panel-head{padding-left:14px;padding-right:14px}td{padding:10px}th{padding:9px 10px}}
</style></head><body><div class="layout"><aside>
<div class="brand"><div class="brand-mark">i</div><div><strong>I2IT Library</strong><small>Knowledge, organized.</small></div></div>
<nav><div class="nav-label">Workspace</div><a class="nav-link active" href="/"><span class="nav-icon">▦</span>Library dashboard</a></nav>
<div class="side-bottom"><strong>International Institute of<br>Information Technology</strong>Hinjawadi, Pune · Est. 2003</div>
</aside><main>
<div class="topline"><div class="crumb">Workspace&nbsp; / &nbsp;<b>Library dashboard</b></div><div class="user"><span>${escape(accountName)}</span><span class="avatar">${escape(accountName.charAt(0).toUpperCase())}</span><form method="post" action="/logout"><button class="action" type="submit">Sign out</button></form></div></div>
<section class="welcome"><div><div class="eyebrow">Campus library</div><h1>Good day, librarian.</h1><p class="sub">A little organization goes a long way. Here's what's happening today.</p></div><div class="today">▦ &nbsp;${today}</div></section>`;

  const message = query.message;
  if (message) {
    const isErr = message.includes("could not") || message.includes("no available") || message.includes("Return all") || message.includes("valid") || message.includes("complete") || message.includes("already");
    html += `<div class="flash${isErr ? ' error' : ''}">${escape(message)}</div>`;
  }

  html += `<section class="stats">
${stat("Catalog titles", books.length, `Across ${categories.length} collections`, "▤")}
${stat("Copies available", books.reduce((a, b) => a + b.available, 0), "Ready to find a new reader", "⌕")}
${stat("Currently borrowed", activeLoans, "Active student checkouts", "↗")}
${stat("Overdue returns", overdue, overdue === 0 ? "Everyone is on track" : "Need a friendly reminder", "◷")}
</section><section class="grid"><div class="panel"><div class="panel-head"><div><h2>Book catalog</h2><p>Browse, manage, and lend your collection</p></div><span class="count">${filtered.length} titles</span></div>
<details class="modal"><summary>＋ &nbsp; Add a book to the catalog</summary><form class="add-form" method="post" action="/books/add">
${field("title", "Book title", "e.g. The Design of Everyday Things", "text")}
${field("author", "Author", "Author name", "text")}
${field("isbn", "ISBN", "ISBN-13", "text")}
${field("category", "Category", "e.g. Technology", "text")}
${field("copies", "Number of copies", "1", "number")}
<button class="primary" type="submit">Add book</button></form></details>
<form class="toolbar" method="get" action="/"><div class="search"><span>⌕</span><input name="q" placeholder="Search title, author, or ISBN..." value="${escape(search)}"></div><select class="filter" name="category"><option value="">All categories</option>`;

  for (const item of categories) {
    html += `<option${item === category ? ' selected' : ''}>${escape(item)}</option>`;
  }

  html += `</select><button class="primary" type="submit">Search</button></form><div class="table-wrap"><table><thead><tr><th>Book</th><th>Category</th><th>Available</th><th>Action</th></tr></thead><tbody>`;

  if (filtered.length === 0) {
    html += `<tr><td class="empty" colspan="4">No books match your search. Try another title or category.</td></tr>`;
  }

  for (const book of filtered) {
    html += `<tr><td><div class="book-name">${escape(book.title)}</div><div class="book-author">${escape(book.author)} · ${escape(book.isbn)}</div></td><td><span class="badge">${escape(book.category)}</span></td><td><span class="availability"><b${book.available === 0 ? ' class="none"' : ''}>${book.available}</b> / ${book.copies}</span></td><td class="row-action">`;
    if (book.available > 0) {
      html += `<details class="borrow"><summary class="action">Check out</summary><form class="borrow-form" method="post" action="/books/borrow"><label>Student name or ID<input name="student" placeholder="e.g. CSE-2042" required></label><input type="hidden" name="bookId" value="${book.id}"><button class="primary" type="submit">Confirm checkout</button></form></details>`;
    } else {
      html += `<span class="badge">All borrowed</span>`;
    }
    if (book.available === book.copies) {
      html += `<form class="remove-form" method="post" action="/books/delete" onsubmit="return confirm('Remove this book from the catalog?')"><input type="hidden" name="bookId" value="${book.id}"><button type="submit" aria-label="Remove book">Remove</button></form>`;
    }
    html += `</td></tr>`;
  }

  html += `</tbody></table></div></div><div class="panel side-panel"><div class="panel-head"><div><h2>Recent checkouts</h2><p>Keep an eye on books out in the wild</p></div><span class="count">${activeLoans} active</span></div><div class="loan-list">`;

  const active = loans.filter(l => !l.returned).sort((a, b) => b.id - a.id).slice(0, 5);
  if (active.length === 0) {
    html += `<div class="empty">No active checkouts. It's a good day to borrow a book.</div>`;
  }

  for (const loan of active) {
    html += `<div class="loan"><div class="loan-art">▤</div><div><div class="loan-title">${escape(loan.title)}</div><div class="loan-meta">${escape(loan.student)}</div><div class="due">Due ${escape(loan.dueDate)}</div></div><form class="return-form" method="post" action="/loans/return"><input type="hidden" name="loanId" value="${loan.id}"><button type="submit">Return</button></form></div>`;
  }

  html += `</div>`;
  if (overdue > 0) {
    html += `<div class="overdue-note">◷ &nbsp; ${overdue} book${overdue === 1 ? ' is' : 's are'} past the return date.</div>`;
  }

  html += `</div></section><footer>I2IT Library · A better way to keep knowledge moving.</footer></main></div></body></html>`;
  return html;
}

// Request Handler
function handler(req, res) {
  const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  let pathname = parsedUrl.pathname;
  if (pathname.startsWith('/api')) {
    pathname = pathname.substring(4) || '/';
  }

  const query = {};
  for (const [k, v] of parsedUrl.searchParams.entries()) {
    query[k] = v;
  }

  const email = getSessionEmail(req);

  if (req.method === 'POST') {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      const form = parseForm(body);

      if (pathname === '/register') {
        const name = (form.name || '').trim();
        const userEmail = (form.email || '').trim().toLowerCase();
        const password = form.password || '';

        if (!name || name.length > 80 || !/^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$/.test(userEmail) || password.length < 8 || password.length > 128) {
          return redirect(res, `/register?message=${encodeURIComponent('Enter your name, a valid email, and a password of at least 8 characters.')}`);
        }
        if (accounts.has(userEmail)) {
          return redirect(res, `/register?message=${encodeURIComponent('An account already exists for that email. Please sign in.')}`);
        }
        const salt = crypto.randomBytes(16).toString('base64');
        const passwordHash = hashPassword(password, salt);
        accounts.set(userEmail, { name, email: userEmail, salt, passwordHash });

        const token = crypto.randomUUID();
        sessions.set(token, userEmail);
        return redirect(res, '/', {
          'Set-Cookie': `I2IT_SESSION=${token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=43200`
        });
      }

      if (pathname === '/login') {
        const userEmail = (form.email || '').trim().toLowerCase();
        const password = form.password || '';
        const acc = accounts.get(userEmail);
        if (!acc || hashPassword(password, acc.salt) !== acc.passwordHash) {
          return redirect(res, `/login?message=${encodeURIComponent('Email or password is incorrect.')}`);
        }
        const token = crypto.randomUUID();
        sessions.set(token, userEmail);
        return redirect(res, '/', {
          'Set-Cookie': `I2IT_SESSION=${token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=43200`
        });
      }

      if (pathname === '/logout') {
        return redirect(res, `/login?message=${encodeURIComponent('You have been signed out.')}`, {
          'Set-Cookie': 'I2IT_SESSION=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0'
        });
      }

      if (!email) {
        return redirect(res, `/login?message=${encodeURIComponent('Please sign in to continue.')}`);
      }

      let message = 'That action could not be found.';

      if (pathname === '/books/add') {
        const title = (form.title || '').trim();
        const author = (form.author || '').trim();
        const isbn = (form.isbn || '').trim();
        const category = (form.category || '').trim();
        const copies = parseInt(form.copies, 10);

        if (!title || !author || !isbn || !category || isNaN(copies) || copies < 1 || copies > 999) {
          message = 'Please complete every field and enter 1–999 copies.';
        } else if (books.some(b => b.isbn.toLowerCase() === isbn.toLowerCase())) {
          message = 'A book with that ISBN is already in the catalog.';
        } else {
          books.push({
            id: nextBookId++,
            title,
            author,
            isbn,
            category,
            copies,
            available: copies
          });
          message = `“${title}” was added to the catalog.`;
        }
      } else if (pathname === '/books/borrow') {
        const bookId = parseInt(form.bookId, 10);
        const student = (form.student || '').trim();
        const book = books.find(b => b.id === bookId);

        if (!book || !student) {
          message = 'Choose a book and enter the student\'s name or ID.';
        } else if (book.available < 1) {
          message = 'There are no available copies of that book.';
        } else {
          book.available -= 1;
          const due = new Date();
          due.setDate(due.getDate() + 14);
          loans.push({
            id: nextLoanId++,
            bookId: book.id,
            title: book.title,
            student,
            dueDate: due.toISOString().split('T')[0],
            returned: false
          });
          message = `“${book.title}” checked out to ${student}.`;
        }
      } else if (pathname === '/loans/return') {
        const loanId = parseInt(form.loanId, 10);
        const loan = loans.find(l => l.id === loanId && !l.returned);
        if (loan) {
          loan.returned = true;
          const book = books.find(b => b.id === loan.bookId);
          if (book) {
            book.available = Math.min(book.copies, book.available + 1);
          }
          message = `“${loan.title}” has been returned.`;
        } else {
          message = 'That active loan could not be found.';
        }
      } else if (pathname === '/books/delete') {
        const bookId = parseInt(form.bookId, 10);
        const bookIndex = books.findIndex(b => b.id === bookId);
        if (bookIndex === -1) {
          message = 'That book could not be found.';
        } else {
          const book = books[bookIndex];
          if (book.available !== book.copies) {
            message = 'Return all copies before removing this book.';
          } else {
            books.splice(bookIndex, 1);
            message = `“${book.title}” was removed from the catalog.`;
          }
        }
      }

      return redirect(res, `/?message=${encodeURIComponent(message)}`);
    });
    return;
  }

  // GET requests
  if (pathname === '/login' || pathname === '/register') {
    if (email) {
      return redirect(res, '/');
    }
    return sendHtml(res, 200, renderAuth(pathname === '/register', query.message));
  }

  if (pathname === '/logout') {
    return redirect(res, '/');
  }

  if (pathname === '/' || pathname === '') {
    if (!email) {
      return redirect(res, '/login');
    }
    const acc = accounts.get(email) || { name: 'Librarian' };
    return sendHtml(res, 200, render(query, acc.name));
  }

  // Fallback 404
  res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end('Page not found');
}

// Support both Vercel Serverless Function and local node server
module.exports = handler;

if (require.main === module) {
  const port = process.env.PORT || 8080;
  const server = http.createServer(handler);
  server.listen(port, () => {
    console.log(`Server listening on http://localhost:${port}`);
  });
}
